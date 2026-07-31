package org.ktz.faceid.storage;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Stores exactly ONE front capture per attempt in a private MinIO bucket.
 * Object key layout: face-auth-captures/YYYY/MM/DD/user-<id>/transaction-<id>.jpg
 * PostgreSQL keeps only key + sha256 + retention date. Raw bytes never touch logs/tables.
 */
@Slf4j
@Service
public class CaptureStorage {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy/MM/dd");

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

    public record Stored(String objectKey, String sha256) {}

    public Stored put(Long userId, UUID transactionId, byte[] jpeg) {
        String shortUser = userId.toString().substring(0, 8);
        String shortTxn = transactionId.toString().substring(0, 8);
        String day = ZonedDateTime.now(ZoneOffset.UTC).format(DAY);
        String key = "%s/user-%s/transaction-%s.jpg".formatted(day, shortUser, shortTxn);
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(jpeg), jpeg.length, -1)
                    .contentType("image/jpeg")
                    .build());
            return new Stored(key, sha256Hex(jpeg));
        } catch (Exception e) {
            throw new RuntimeException("MinIO put failed", e);
        }
    }

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
