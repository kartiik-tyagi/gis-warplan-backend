package com.drdo.jayant.config;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Ensures the PostgreSQL database and PostGIS extension exist before Hibernate connects.
 * Tables are left to Hibernate. Runs for every profile where jayant.db.auto-create is true,
 * including production. The test profile turns the flag off.
 */
public class DatabaseBootstrap implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final Logger log = LogManager.getLogger(DatabaseBootstrap.class);
    private static final String DUPLICATE_DATABASE = "42P04";

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        if (!environment.getProperty("jayant.db.auto-create", Boolean.class, true)) {
            return;
        }
        DriverManager.setLoginTimeout(5);
        String url = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        if (url == null || !url.startsWith("jdbc:postgresql://")) {
            throw stop("Bootstrap supports PostgreSQL only.", null);
        }
        if (username == null || username.isBlank()) {
            throw stop("Database username is required.", null);
        }
        if (password == null || password.isBlank()) {
            throw stop("Database password is required.", null);
        }

        Target target = readTarget(url);
        createDatabase(target, username, password);
        enablePostgis(url, username, password, target.name());
    }

    private static Target readTarget(String url) {
        try {
            URI uri = URI.create(url.substring("jdbc:".length()));
            String path = uri.getPath() == null ? "" : uri.getPath();
            String name = path.startsWith("/") ? path.substring(1) : path;
            int slash = name.indexOf('/');
            if (slash >= 0) {
                name = name.substring(0, slash);
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw stop("Datasource URL could not be read.", null);
            }
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String query = uri.getRawQuery();
            String maintenance = "jdbc:postgresql://" + uri.getHost() + ":" + port + "/postgres"
                    + (query == null ? "" : "?" + query);
            return new Target(DatabaseName.requireIdentifier(name), maintenance);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw stop("Datasource URL could not be read.", exception);
        }
    }

    private static void createDatabase(Target target, String username, String password) {
        try (Connection connection = DriverManager.getConnection(target.maintenanceUrl(), username, password)) {
            connection.setAutoCommit(true);
            if (databaseExists(connection, target.name())) {
                log.info("Database {} already exists", target.name());
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE \"" + target.name() + "\"");
            }
            log.info("Created database {}", target.name());
        } catch (SQLException exception) {
            if (DUPLICATE_DATABASE.equals(exception.getSQLState())) {
                log.info("Database {} already exists", target.name());
                return;
            }
            throw stop("PostgreSQL could not be reached to prepare database " + target.name() + ".", exception);
        }
    }

    private static boolean databaseExists(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pg_database WHERE datname = ?")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void enablePostgis(String url, String username, String password, String name) {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE EXTENSION IF NOT EXISTS postgis");
            log.info("PostGIS extension is available on {}", name);
        } catch (SQLException exception) {
            throw stop("PostGIS could not be enabled on database " + name + ".", exception);
        }
    }

    private static IllegalStateException stop(String message, Exception cause) {
        if (cause == null) {
            log.error(message);
            return new IllegalStateException(message);
        }
        log.error(message, cause);
        return new IllegalStateException(message, cause);
    }

    private record Target(String name, String maintenanceUrl) {
    }
}
