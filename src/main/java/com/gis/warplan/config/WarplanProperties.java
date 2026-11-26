package com.gis.warplan.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "warplan")
public class WarplanProperties {

    /**
     * Display title used only when {@code warplan.application-name} is missing.
     * The setting in application.yml is the title to change.
     */
    private String applicationName = "CBSMD";
    private String apiBasePath = "/api/v1";
    private List<String> corsOrigins = new ArrayList<>(List.of("http://127.0.0.1:5173"));
    private final Db db = new Db();
    private final Jwt jwt = new Jwt();
    private final Icons icons = new Icons();
    private final Attachments attachments = new Attachments();
    private final Publish publish = new Publish();

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName == null || applicationName.isBlank() ? "CBSMD" : applicationName.trim();
    }

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
        if (corsOrigins == null) {
            this.corsOrigins = List.of();
            return;
        }
        this.corsOrigins = corsOrigins.stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
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

    public Attachments getAttachments() {
        return attachments;
    }

    public Publish getPublish() {
        return publish;
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
        private String root = "asset-repository";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root == null || root.isBlank() ? "asset-repository" : root;
        }
    }

    public static class Attachments {
        private String root = "images/scenario-attachments";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root == null || root.isBlank() ? "images/scenario-attachments" : root;
        }
    }

    public static class Publish {
        private String stagingRoot = "publish-staging";
        private int maxItemsPerJob = 100;
        private long maxFileBytes = 100L * 1024 * 1024;
        private int workerConcurrency = 3;

        public String getStagingRoot() {
            return stagingRoot;
        }

        public void setStagingRoot(String stagingRoot) {
            this.stagingRoot = stagingRoot == null || stagingRoot.isBlank() ? "publish-staging" : stagingRoot;
        }

        public int getMaxItemsPerJob() {
            return maxItemsPerJob;
        }

        public void setMaxItemsPerJob(int maxItemsPerJob) {
            this.maxItemsPerJob = maxItemsPerJob;
        }

        public long getMaxFileBytes() {
            return maxFileBytes;
        }

        public void setMaxFileBytes(long maxFileBytes) {
            this.maxFileBytes = maxFileBytes;
        }

        public int getWorkerConcurrency() {
            return workerConcurrency;
        }

        public void setWorkerConcurrency(int workerConcurrency) {
            this.workerConcurrency = workerConcurrency;
        }
    }
}
