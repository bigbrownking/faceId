package org.ktz.faceid.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ktz.faceid.domain.capture.FaceCapture;
import org.ktz.faceid.repository.FaceCaptureRepository;
import org.ktz.faceid.storage.CaptureStorage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Deletes captures past their retention deadline (capturedAt + retention.capture-days).
 * Source of truth is Postgres (face_capture); MinIO objects are removed by object_key.
 * No folder scanning — the DB knows every key and its deadline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionJob {

    private final FaceCaptureRepository repo;
    private final CaptureStorage storage;

    /** Every day at 03:00 UTC. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpired() {
        List<FaceCapture> expired = repo.findByRetentionUntilBefore(Instant.now());
        int deleted = 0;
        for (FaceCapture c : expired) {
            try {
                storage.deleteByPath(c.getObjectKey());
                repo.delete(c);
                deleted++;
            } catch (Exception e) {
                log.warn("Failed to purge capture {}: {}", c.getObjectKey(), e.getMessage());
            }
        }
        if (deleted > 0) log.info("Purged {} expired captures", deleted);
    }
}