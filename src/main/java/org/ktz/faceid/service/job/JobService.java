package org.ktz.faceid.service.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ktz.faceid.domain.job.*;
import org.ktz.faceid.repository.FaceJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final FaceJobRepository repo;
    private final JobEventRedisPublisher eventPublisher;
    private final SecureRandom rnd = new SecureRandom();

    /** Create a QUEUED job. userId null => anonymous, a jobToken is generated. */
    @Transactional
    public FaceJob create(JobType type, Long userId, UUID challengeId, Instant expiresAt) {
        FaceJob j = new FaceJob();
        j.setJobId(UUID.randomUUID());
        j.setType(type);
        j.setStatus(JobStatus.QUEUED);
        j.setUserId(userId);
        j.setChallengeId(challengeId);
        j.setExpiresAt(expiresAt);
        if (userId == null) {
            j.setJobToken(generateToken());
        }
        return repo.save(j);
    }

    @Transactional
    public void markRunning(UUID jobId) {
        FaceJob j = repo.findById(jobId).orElseThrow();
        j.setStatus(JobStatus.RUNNING);
        repo.save(j);
        eventPublisher.publish(j);
    }

    @Transactional
    public void succeed(UUID jobId, Map<String, Object> result) {
        FaceJob j = repo.findById(jobId).orElseThrow();
        j.setStatus(JobStatus.SUCCEEDED);
        j.setResult(result);
        j.setFinishedAt(Instant.now());
        repo.save(j);
        eventPublisher.publish(j);
    }

    @Transactional
    public void fail(UUID jobId, JobStatus status, String code, String message) {
        FaceJob j = repo.findById(jobId).orElseThrow();
        j.setStatus(status); // FAILED / REJECTED
        j.setErrorCode(code);
        j.setErrorMessage(message);
        j.setFinishedAt(Instant.now());
        repo.save(j);
        eventPublisher.publish(j);
    }

    @Transactional
    public boolean cancel(UUID jobId) {
        FaceJob j = repo.findById(jobId).orElseThrow();
        if (j.getStatus() == JobStatus.QUEUED) {
            j.setStatus(JobStatus.CANCELLED);
            j.setFinishedAt(Instant.now());
            repo.save(j);
            return true;
        }
        return false; // already running/finished — can't cancel
    }

    /** Access for polling: authenticated owner OR anonymous with jobToken. */
    @Transactional(readOnly = true)
    public FaceJob getForAccess(UUID jobId, Long userId, String jobToken) {
        if (userId != null) {
            return repo.findByJobIdAndUserId(jobId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        }
        if (jobToken != null) {
            return repo.findByJobIdAndJobToken(jobId, jobToken)
                    .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        }
        throw new IllegalArgumentException("No credentials to access job");
    }

    private String generateToken() {
        byte[] b = new byte[32];
        rnd.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}