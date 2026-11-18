package com.gis.warplan.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "warplan")
public class WarplanProperties {

    private String apiBasePath = "/api/v1";
    private List<String> corsOrigins = new ArrayList<>(List.of("http://127.0.0.1:5173"));
    private final Db db = new Db();
    private final Jwt jwt = new Jwt();
    private final Icons icons = new Icons();

    public String getApiBasePath() {
        return apiBasePath;
    }

    public void setApiBasePath(String apiBasePath) {
        this.apiBasePath = apiBasePath;
    }

    public List<String> getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(List<String> corsOrigins) {
        this.corsOrigins = corsOrigins == null ? List.of() : List.copyOf(corsOrigins);
    }

    public Db getDb() {
        return db;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public Icons getIcons() {
        return icons;
    }

    public static class Db {
        private boolean autoCreate = true;

        public boolean isAutoCreate() {
            return autoCreate;
        }

        public void setAutoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
        }
    }

    public static class Jwt {
        private String secret = "";
        private long expiration = 90_000L;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpiration() {
            return expiration;
        }

        public void setExpiration(long expiration) {
            this.expiration = expiration;
        }
    }

    public static class Icons {
        private String root = "images/icon";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root == null || root.isBlank() ? "images/icon" : root;
        }
    }
}
