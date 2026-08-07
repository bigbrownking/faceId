package org.ktz.faceid.service.face;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ktz.faceid.domain.capture.FaceCapture;
import org.ktz.faceid.domain.reference.FaceReferenceSet;
import org.ktz.faceid.repository.FaceCaptureRepository;
import org.ktz.faceid.repository.ReferenceSetRepository;
import org.ktz.faceid.storage.CaptureStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Full Face ID reset for a single user.
 *
 * Wipes the user's enrolled biometric material:
 *   - every face_reference_set (any status) and its embeddings (cascade / orphanRemoval)
 *   - REFERENCE face_capture rows and their MinIO objects
 *
 * ATTEMPT captures (login-attempt frames) are deliberately KEPT — they are an
 * audit trail of access attempts and outlive a Face ID reset. They expire on
 * their own via RetentionJob.
 *
 * DB removal runs in one transaction. MinIO object deletion is best-effort:
 * a storage failure is logged but never rolls back the metadata purge, mirroring
 * the capture-persistence policy (storage must not block the primary flow).
 * After this call, existsByUserIdAndStatus(userId, ACTIVE) is false, so the
 * orchestrator flips face_enabled = false on its side.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaceIdResetService {

    private final ReferenceSetRepository setRepo;
    private final FaceCaptureRepository captureRepo;
    private final CaptureStorage storage;

    /**
     * @return a small summary of what was removed (useful for the orchestrator log).
     */
    @Transactional
    public Result reset(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }

        List<FaceReferenceSet> sets = setRepo.findByUserId(userId);
        int embeddings = sets.stream().mapToInt(s -> s.getEmbeddings() == null ? 0 : s.getEmbeddings().size()).sum();
        if (!sets.isEmpty()) {
            setRepo.deleteAll(sets);
        }

        List<FaceCapture> captures = captureRepo.findByUserIdAndCaptureType(userId, "REFERENCE");
        int objectsDeleted = 0;
        for (FaceCapture c : captures) {
            try {
                storage.deleteByPath(c.getObjectKey());
                objectsDeleted++;
            } catch (Exception e) {
                log.warn("Face reset: failed to delete object {} for user {}: {}",
                        c.getObjectKey(), userId, e.getMessage());
            }
        }
        if (!captures.isEmpty()) {
            captureRepo.deleteAll(captures);
        }

        log.info("Face reset for user {}: sets={}, embeddings={}, referenceCaptures={}, objectsDeleted={} (ATTEMPT captures kept)",
                userId, sets.size(), embeddings, captures.size(), objectsDeleted);

        return new Result(sets.size(), embeddings, captures.size(), objectsDeleted);
    }

    public record Result(int referenceSets, int embeddings, int referenceCaptures, int objectsDeleted) {}
}