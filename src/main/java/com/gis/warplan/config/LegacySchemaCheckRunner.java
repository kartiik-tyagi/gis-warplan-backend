package com.gis.warplan.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Legacy tables from before the side/functionality rename. Hibernate {@code ddl-auto: update} never renames or
 * drops columns. After SCHEMA-LOCK changes, drop and recreate {@code gis_warplan} once (see docs/SCHEMA-LOCK.md).
 * This runner only logs when it finds old tables; it never changes the schema or stops startup.
 */
@Component
@Order(1)
public class LegacySchemaCheckRunner implements ApplicationRunner {

    private static final Logger log = LogManager.getLogger(LegacySchemaCheckRunner.class);

    private static final List<String> LEGACY_TABLES = List.of("team", "feature");

    private final DataSource dataSource;

    public LegacySchemaCheckRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection();
                ResultSet tables = connection.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
            Set<String> found = new TreeSet<>();
            while (tables.next()) {
                String name = tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                if (LEGACY_TABLES.contains(name)) {
                    found.add(name);
                }
            }
            if (!found.isEmpty()) {
                log.error("Legacy tables {} found: this database predates the side/functionality/game-owned ORBAT "
                        + "schema. Drop the database once and restart so Hibernate recreates it "
                        + "(no migration files are provided).", found);
            }
        } catch (Exception ex) {
            log.warn("Legacy schema check skipped: {}", ex.getMessage());
        }
    }
}
