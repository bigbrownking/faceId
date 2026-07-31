package org.ktz.faceid.repository;

import org.ktz.faceid.domain.job.FaceJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FaceJobRepository extends JpaRepository<FaceJob, UUID> {

    // Anonymous access must present the matching jobToken.
    Optional<FaceJob> findByJobIdAndJobToken(UUID jobId, String jobToken);

    // Authenticated access by owner.
    Optional<FaceJob> findByJobIdAndUserId(UUID jobId, Long userId);
}