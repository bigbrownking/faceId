package org.ktz.faceid.storage;

import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Private MinIO bucket for face captures.
 *
 * Two key layouts coexist:
 *   - legacy (put): YYYY/MM/DD/user-<id>/transaction-<id>.jpg   (single front capture)
 *   - new    :      {userId}/reference/{pose}.jpg[.enc]
 *                   {userId}/attempts/{yyyy-MM-dd}/{jobId}_{action}.jpg[.enc]
 *
 * object_key persisted in Postgres is the FULL path with bucket prefix
 * ("{bucket}/{name}"), consistent with the orchestrator's CaseFile.fileUrl.
 * extractObjectName() strips the bucket before talking to MinIO.
 */
@Slf4j
@Service
public class CaptureStorage {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter DAY_DASH =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final MinioClient client;
    private final String bucket;

    public CaptureStorage(@Value("${minio.endpoint}") String endpoint,
                          @Value("${minio.access-key}") String accessKey,
                          @Value("${minio.secret-key}") String secretKey,
                          @Value("${minio.bucket}") String bucket) {
        this.bucket = bucket;
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @PostConstruct
    public void ensureBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created private MinIO bucket '{}'", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket check failed (is MinIO running?): {}", e.getMessage());
        }
    }

    public String getBucket() { return bucket; }

    // ================= NEW: reference / attempt layout =================

    /** Full path: {bucket}/{userId}/reference/{pose}.jpg[.enc] */
    public String referenceObjectPath(Long userId, String pose, boolean enc) {
        String name = "%d/reference/%s.jpg%s".formatted(userId, pose, enc ? ".enc" : "");
        return bucket + "/" + name;
    }

    /** Full path: {bucket}/{userId}/attempts/{yyyy-MM-dd}/{jobId}_{action}.jpg[.enc] */
    public String attemptObjectPath(Long userId, UUID jobId, String action, boolean enc) {
        String day = DAY_DASH.format(Instant.now());
        String name = "%d/attempts/%s/%s_%s.jpg%s".formatted(userId, day, jobId, action, enc ? ".enc" : "");
        return bucket + "/" + name;
    }

    /** Upload bytes to a FULL objectPath ({bucket}/{name}); bucket is stripped. */
    public void put(String objectPath, byte[] data, String contentType) {
        String name = extractObjectName(objectPath);
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(name)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("MinIO put failed: " + name, e);
        }
    }

    /** Download raw bytes by FULL objectPath (used by monitoring decrypt endpoint). */
    public byte[] getBytes(String objectPath) {
        String name = extractObjectName(objectPath);
        try (var is = client.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(name).build())) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("MinIO get failed: " + name, e);
        }
    }

    /** Presigned GET by FULL objectPath. Returns encrypted bytes if the object is .enc. */
    public String presignedGet(String objectPath, int expirySeconds) {
        String name = extractObjectName(objectPath);
        try {
            return client.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(name)
                            .expiry(expirySeconds)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("presign failed: " + name, e);
        }
    }

    /** Delete by FULL objectPath ({bucket}/{name}). */
    public void deleteByPath(String objectPath) {
        delete(extractObjectName(objectPath));
    }

    /** {bucket}/name -> name. Tolerates a bare name too. */
    private String extractObjectName(String objectPath) {
        if (objectPath == null || objectPath.isEmpty())
            throw new IllegalArgumentException("Object path cannot be empty");
        if (objectPath.startsWith(bucket + "/"))
            return objectPath.substring(bucket.length() + 1);
        return objectPath;
    }

    /** Delete by bare object name (no bucket prefix). */
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            log.warn("Failed to delete {}: {}", objectKey, e.getMessage());
        }
    }

    public static String sha256Hex(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}