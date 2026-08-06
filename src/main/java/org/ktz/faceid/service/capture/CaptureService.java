package org.ktz.faceid.service.capture;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ktz.faceid.crypto.EnvelopeCrypto;
import org.ktz.faceid.domain.capture.FaceCapture;
import org.ktz.faceid.repository.FaceCaptureRepository;
import org.ktz.faceid.storage.CaptureStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Persists face photos (reference + login attempts) to MinIO with metadata in Postgres.
 * Storage failures NEVER break enroll/verify — they are logged and swallowed.
 *
 * When capture.encrypt=true, photos are envelope-encrypted (same scheme as embeddings)
 * and stored as .enc blobs — not viewable directly in MinIO console; use the
 * monitoring decrypt endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptureService {

    private final CaptureStorage storage;
    private final EnvelopeCrypto crypto;
    private final FaceCaptureRepository repo;

    @Value("${retention.capture-days}")
    private int retentionDays;

    @Value("${capture.encrypt}")
    private boolean encrypt;

    /** 3 enrollment reference photos. No-op for anonymous (userId == null) prepare. */
    public void saveReference(Long userId, UUID jobId, String pose, byte[] jpeg) {
        if (userId == null || jpeg == null || jpeg.length == 0) return;
        store(userId, jobId, pose, "REFERENCE", jpeg,
                storage.referenceObjectPath(userId, pose, encrypt));
    }

    /** Every login attempt frame. No-op for anonymous (userId == null). */
    public void saveAttempt(Long userId, UUID jobId, String action, byte[] jpeg) {
        if (userId == null || jpeg == null || jpeg.length == 0) return;
        store(userId, jobId, action, "ATTEMPT", jpeg,
                storage.attemptObjectPath(userId, jobId, action, encrypt));
    }

    private void store(Long userId, UUID jobId, String pose, String type,
                       byte[] jpeg, String objectPath) {
        try {
            String sha = CaptureStorage.sha256Hex(jpeg);          // hash of original
            byte[] payload = encrypt ? crypto.sealToBlob(jpeg) : jpeg;
            String contentType = encrypt ? "application/octet-stream" : "image/jpeg";

            storage.put(objectPath, payload, contentType);

            Instant now = Instant.now();
            FaceCapture c = new FaceCapture();
            c.setId(UUID.randomUUID());
            c.setUserId(userId);
            c.setJobId(jobId);
            c.setPose(pose);
            c.setCaptureType(type);
            c.setObjectKey(objectPath);   // full path with bucket prefix
            c.setSha256(sha);
            c.setEncrypted(encrypt);
            c.setCapturedAt(now);
            c.setRetentionUntil(now.plus(Duration.ofDays(retentionDays)));
            repo.save(c);
        } catch (Exception e) {
            // capture persistence must not fail authentication
            log.warn("Failed to store capture {}: {}", objectPath, e.getMessage());
        }
    }
}