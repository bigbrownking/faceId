package org.ktz.faceid.service.face;

import lombok.RequiredArgsConstructor;
import org.ktz.faceid.domain.job.FaceJob;
import org.ktz.faceid.domain.job.JobStatus;
import org.ktz.faceid.domain.reference.FaceReferenceSet;
import org.ktz.faceid.domain.reference.ReferenceStatus;
import org.ktz.faceid.repository.FaceJobRepository;
import org.ktz.faceid.repository.ReferenceSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * After registration, bind the anonymous reference-set (created during the
 * prepare job) to the newly created user id.
 */
@Service
@RequiredArgsConstructor
public class ReferenceAdoptionService {

    private final FaceJobRepository jobRepo;
    private final ReferenceSetRepository setRepo;

    @Transactional
    public boolean adopt(UUID jobId, String jobToken, Long userId) {
        FaceJob job = jobRepo.findByJobIdAndJobToken(jobId, jobToken).orElse(null);
        if (job == null || job.getStatus() != JobStatus.SUCCEEDED) return false;

        Object refId = job.getResult() == null ? null : job.getResult().get("referenceSetId");
        if (refId == null) return false;

        FaceReferenceSet set = setRepo.findById(UUID.fromString(refId.toString())).orElse(null);
        if (set == null) return false;

        set.setUserId(userId);
        set.setStatus(ReferenceStatus.ACTIVE);
        setRepo.save(set);

        job.setUserId(userId);
        jobRepo.save(job);
        return true;
    }
}