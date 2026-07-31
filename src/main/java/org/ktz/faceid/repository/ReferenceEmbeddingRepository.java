package org.ktz.faceid.repository;

import org.ktz.faceid.domain.reference.FaceReferenceEmbedding;
import org.ktz.faceid.domain.reference.FaceReferenceSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferenceEmbeddingRepository extends JpaRepository<FaceReferenceEmbedding, UUID> {

    List<FaceReferenceEmbedding> findByReferenceSet(FaceReferenceSet set);

    Optional<FaceReferenceEmbedding> findByReferenceSetAndPose(FaceReferenceSet set, String pose);
}