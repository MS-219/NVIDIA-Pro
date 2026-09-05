package com.juxin.orin.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String publicBaseUrl = "https://api.example.com";
    private String jwtSecret;
    private String nodeAdminJwtSecret;
    private Admin admin = new Admin();
    private Sms sms = new Sms();
    private Update update = new Update();

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }
    public String getNodeAdminJwtSecret() { return nodeAdminJwtSecret; }
    public void setNodeAdminJwtSecret(String value) { this.nodeAdminJwtSecret = value; }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public static class Admin {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public Sms getSms() {
        return sms;
    }

    public Update getUpdate() { return update; }
    public void setUpdate(Update update) { this.update = update; }

    public static class Update {
        private String storageDir = "./uploads/app-releases";
        private long maxFileBytes = 500L * 1024 * 1024;
        public String getStorageDir() { return storageDir; }
        public void setStorageDir(String storageDir) { this.storageDir = storageDir; }
        public long getMaxFileBytes() { return maxFileBytes; }
        public void setMaxFileBytes(long maxFileBytes) { this.maxFileBytes = maxFileBytes; }
    }

    public void setSms(Sms sms) {
        this.sms = sms;
    }

    public static class Sms {
        private String provider = "mock";
        private String pepper;
        private int codeTtlSeconds = 300;
        private int cooldownSeconds = 60;
        private int maxAttempts = 5;
        private Aliyun aliyun = new Aliyun();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getPepper() {
            return pepper;
        }

        public void setPepper(String pepper) {
            this.pepper = pepper;
        }

        public int getCodeTtlSeconds() {
            return codeTtlSeconds;
        }

        public void setCodeTtlSeconds(int codeTtlSeconds) {
            this.codeTtlSeconds = codeTtlSeconds;
        }

        public int getCooldownSeconds() {
            return cooldownSeconds;
        }

        public void setCooldownSeconds(int cooldownSeconds) {
            this.cooldownSeconds = cooldownSeconds;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Aliyun getAliyun() {
            return aliyun;
        }

        public void setAliyun(Aliyun aliyun) {
            this.aliyun = aliyun;
        }
    }

    public static class Aliyun {
        private String accessKeyId;
        private String accessKeySecret;
        private String endpoint = "dysmsapi.aliyuncs.com";
        private String signName;
        private String templateCode;
        private String codeParameterName = "code";
        private int connectTimeoutMillis = 5_000;
        private int readTimeoutMillis = 10_000;

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getSignName() {
            return signName;
        }

        public void setSignName(String signName) {
            this.signName = signName;
        }

        public String getTemplateCode() {
            return templateCode;
        }

        public void setTemplateCode(String templateCode) {
            this.templateCode = templateCode;
        }

        public String getCodeParameterName() {
            return codeParameterName;
        }

        public void setCodeParameterName(String codeParameterName) {
            this.codeParameterName = codeParameterName;
        }

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public int getReadTimeoutMillis() {
            return readTimeoutMillis;
        }

        public void setReadTimeoutMillis(int readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
        }
    }
}
