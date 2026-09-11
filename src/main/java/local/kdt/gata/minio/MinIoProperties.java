package local.kdt.gata.minio;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio-service")
public class MinIoProperties {
    private String accessKey;
    private String secretKey;
    private String minioUrl;
    private String bucket;
    private boolean notifyOnBaseFolder = true;

    public String getAccessKey() {
        return accessKey;
    }
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getMinioUrl() {
        return minioUrl;
    }
    public void setMinioUrl(String minioUrl) {
        this.minioUrl = minioUrl;
    }

    public String getBucket() {
        return bucket;
    }
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public boolean isNotifyOnBaseFolder() {
        return notifyOnBaseFolder;
    }
    public void setNotifyOnBaseFolder(boolean notifyOnBaseFolder) {
        this.notifyOnBaseFolder = notifyOnBaseFolder;
    }

    @Override
    public String toString() {
        return "MinIoProperties{" +
                "accessKey='" + accessKey + '\'' +
                ", secretKey='" + secretKey + '\'' +
                ", minioUrl='" + minioUrl + '\'' +
                ", bucket='" + bucket + '\'' +
                '}';
    }
}
