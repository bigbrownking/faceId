package org.ktz.faceid.repository;

import org.ktz.faceid.domain.reference.FaceReferenceSet;
import org.ktz.faceid.domain.reference.ReferenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferenceSetRepository extends JpaRepository<FaceReferenceSet, UUID> {

    Optional<FaceReferenceSet> findByUserIdAndStatus(Long userId, ReferenceStatus status);

    boolean existsByUserIdAndStatus(Long userId, ReferenceStatus status);
    List<FaceReferenceSet> findByUserId(Long userId);
}