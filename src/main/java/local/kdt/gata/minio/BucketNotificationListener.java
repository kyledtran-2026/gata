package local.kdt.gata.minio;

public interface BucketNotificationListener {
    /**
     * Notify listeners a new object is ready to be ingested
     * @param folder
     * @param objectName
     * @param objectSize
     */
    boolean minioBucketNotification(String folder, String objectName, long objectSize);

}
