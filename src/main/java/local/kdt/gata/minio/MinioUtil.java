package local.kdt.gata.minio;

import io.micrometer.core.instrument.util.StringUtils;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.messages.DeleteRequest;
import io.minio.messages.DeleteResult;
import io.minio.messages.Item;
import local.kdt.gata.common.util.ContentTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.*;


public class MinioUtil {
    private static final Logger LOG = LoggerFactory.getLogger(MinioUtil.class);


    public static String conditionFolder(String folder) {
        if ( StringUtils.isNotBlank(folder) ) {
            if ( !folder.endsWith("/") )
                folder += "/";
        } else if ( folder==null )
            folder = "";
        return folder;
    }


    public static String[] extractFolderAndName(String objectName) {
        if ( objectName.endsWith("/") )
            objectName = objectName.substring(0, objectName.length()-1);
        int idx = objectName.lastIndexOf('/')+1;
        String folder = "";
        if ( idx>1 ) {
            folder = objectName.substring(0, idx);
            objectName = objectName.substring(idx);
            return new String[] { folder, objectName };
        }
        return new String[] { folder, objectName };
    }


    public static boolean objectExists(MinioClient minioClient, String bucket, String key) {
        StatObjectResponse objectStat = getObjectStat(minioClient, bucket, key);
        return objectStat!=null;
    }


    public static boolean objectExists(MinioClient minioClient, String bucket, String folder, String name) {
        folder = conditionFolder(folder);
        String objectName = folder + name;
        StatObjectResponse objectStat = getObjectStat(minioClient, bucket, objectName);
        return objectStat!=null;
    }

    public static StatObjectResponse getObjectStat(MinioClient minioClient, String bucket, String folder, String filename) {
        folder = conditionFolder(folder);
        String objectName = folder + filename;
        return getObjectStat(minioClient, bucket, objectName);
    }

    public static GetObjectResponse getObjectStream(MinioClient minioClient, String bucket, String folder, String filename) throws MinioException {
        String objectName = folder + filename;
        return getObjectStream(minioClient, bucket, objectName);
    }

    public static GetObjectResponse getObjectStream(MinioClient minioClient, String bucket, String key) throws MinioException {
        GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        return response;
    }

    public static StatObjectResponse getObjectStat(MinioClient minioClient, String bucket, String objectName) {
        try {
            StatObjectResponse objectStat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            return objectStat;
        } catch (Exception ex) {
        }
        return null;
    }


    public static void deleteObject(MinioClient minioClient, String bucket, String folder, String name) throws Exception {
        folder = conditionFolder(folder);
        String objectName = folder+name;
        minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
    }


    public static List<String> deleteObjects(MinioClient minioClient, String bucket, String folder, Set<String> names) {
        folder = conditionFolder(folder);
        List<DeleteRequest.Object> objects = new LinkedList<>();
        for (String name : names) {
            objects.add(new DeleteRequest.Object(folder + name));
        }


        Iterable<Result<DeleteResult.Error>> results = minioClient.removeObjects(
                RemoveObjectsArgs.builder()
                        .bucket(bucket)
                        .objects(objects)
                        .build());


        List<String> errorMsgs = new ArrayList<>();
        for (Result<DeleteResult.Error> result : results) {
            try {
                DeleteResult.Error error = result.get();
                errorMsgs.add("Error in deleting object " + error.objectName() + "; " + error.message());
            } catch (Exception ex) {
                LOG.warn("deleteObject ", ex.getMessage());
            }
        }
        return errorMsgs;
    }


    public static List<String> listObjects(MinioClient minioClient, String bucket, boolean recursive) {
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucket).recursive(recursive).build());
        List<String> objectNames = new ArrayList<>();
        for (Result<Item> r: results) {
            try {
                Item item = r.get();
                objectNames.add(URLDecoder.decode(item.objectName(), "UTF-8"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return objectNames;
    }


    public static List<String> listObjects(MinioClient minioClient, String bucket, String folder) {
        folder = conditionFolder(folder);
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucket).recursive(true).build());
        List<String> objectNames = new ArrayList<>();
        for (Result<Item> r: results) {
            try {
                Item item = r.get();
                String objectName = URLDecoder.decode(item.objectName(), "UTF-8");
                String[] folderAndName = extractFolderAndName(objectName);
                if (folder.equals(folderAndName[0]))
                    objectNames.add(folderAndName[1]);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return objectNames;
    }

    public static void moveObject(MinioClient mc, String bucket, String sourceKey, String destinationKey) throws Exception {
        mc.copyObject(
                CopyObjectArgs.builder()
                        .bucket(bucket)
                        .object(destinationKey)
                        .source(SourceObject.builder()
                                        .bucket(bucket)
                                        .object(sourceKey)
                                        .build())
                        .build());
        mc.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(sourceKey)
                        .build());
    }

    public static void putTextIntoBucketFolderFile(MinioClient minioClient, String bucket, String folder,
                                                   String filename, String content) throws Exception {
        byte[] data = content.getBytes();
        String name = folder+filename;
        putBytesIntoBucketFolder(minioClient, bucket, name, data);
    }

    public static void putBytesIntoBucketFolder(MinioClient minioClient, String bucket, String folder, String filename, byte[] data) throws Exception {
        putBytesIntoBucketFolder(minioClient, bucket, folder+filename, data);
    }

    public static void putBytesIntoBucketFolder(MinioClient minioClient, String bucket, String key, byte[] data) throws Exception {
        String contentType = ContentTypeUtils.fromFilename(key);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .stream(new ByteArrayInputStream(data), (long)data.length, -1L)
                        .contentType(contentType)
                        .build());
    }


    public static void putFileIntoBucketFolder(MinioClient minioClient, String bucket, String key, File file) throws Exception {
        String contentType = ContentTypeUtils.fromFilename(file.getName());
        byte[] fileContent = Files.readAllBytes(file.toPath());
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .stream(new ByteArrayInputStream(fileContent), (long)fileContent.length, -1L)
                        .contentType(contentType)
                        .build());
    }

    public static void putFileIntoBucketFolder(MinioClient minioClient, String bucket, String key, MultipartFile file) throws Exception {
        String contentType = file.getContentType();
        if ( contentType==null || contentType.isBlank() )
            contentType = ContentTypeUtils.fromFilename(file.getOriginalFilename());
        try (InputStream stream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .stream(stream, file.getSize(), -1L) // known size, auto part size
                            .contentType(contentType)
                            .build()
            );
        }
    }

    public static List<S3Object> getS3Objects(MinioClient mc, String bucket) throws Exception {
        List<S3Object> results = fillFolder(mc, bucket, "");
        return results;
    }

    public static List<S3Object> fillFolder(MinioClient mc, String bucket, String path) throws Exception {
        List<S3Object> s3Objects = new ArrayList<>();
        Iterable<Result<Item>> results = mc.listObjects(ListObjectsArgs.builder().bucket(bucket).prefix(path).build());
        Iterator<Result<Item>> it = results.iterator();
        while ( it.hasNext() ) {
            Result<Item> ri = it.next();
            Item item = ri.get();
            String[] folderAndName = extractFolderAndName(item.objectName());
            S3Object s3Object = new S3Object();
            s3Objects.add(s3Object);
            s3Object.setName(folderAndName[1]);
            if ( item.isDir() ) {
                s3Object.setType(S3Type.Folder);
                List<S3Object> subObjects = fillFolder(mc, bucket, item.objectName());
                s3Object.setList(subObjects);
            } else {
                s3Object.setType(S3Type.Object);
                s3Object.setSize(item.size());
            }
        }
        return s3Objects;
    }

    public static void ensureFolder(MinioClient mc, String minioBucket, String folder) throws Exception {
        if (folder == null || folder.isBlank()) {
            return;
        }

        String objectName = folder.endsWith("/") ? folder : folder + "/";

        try {
            mc.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(objectName)
                            .build());
            LOG.info("MinIO folder '{}/{}' already exists", minioBucket, objectName);
        } catch (ErrorResponseException e) {
            if (!"NoSuchKey".equals(e.errorResponse().code())) {
                throw e;
            }
            mc.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(new byte[0]), 0L, -1L)
                            .build());
            LOG.info("Created MinIO folder '{}/{}'", minioBucket, objectName);
        }
    }
}
