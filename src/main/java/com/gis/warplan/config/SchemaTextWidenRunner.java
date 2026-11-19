package com.gis.warplan.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Hibernate {@code ddl-auto: update} does not widen existing {@code varchar} columns to
 * {@code text}. Narrative fields on scenario/game must be text for long rich HTML.
 */
@Component
@Order(0)
public class SchemaTextWidenRunner implements ApplicationRunner {

    private static final Logger log = LogManager.getLogger(SchemaTextWidenRunner.class);

    private final DataSource dataSource;

    public SchemaTextWidenRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        widen("scenario", "general_idea");
        widen("scenario", "opening_narrative");
        widen("scenario", "special_idea");
        widen("game", "description");
    }

    private void widen(String table, String column) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!isVarchar(connection, table, column)) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "ALTER TABLE " + table + " ALTER COLUMN " + column + " TYPE text");
            }
            log.info("Widened {}.{} from varchar to text", table, column);
        }
    }

    private static boolean isVarchar(Connection connection, String table, String column) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT data_type, character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return false;
                }
                String type = result.getString("data_type");
                return "character varying".equalsIgnoreCase(type);
            }
        }
    }
}
