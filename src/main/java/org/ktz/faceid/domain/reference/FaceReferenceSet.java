package org.ktz.faceid.domain.reference;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "face_reference_set", indexes = {
        @Index(name = "idx_refset_user_status", columnList = "user_id, status")
})
public class FaceReferenceSet {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReferenceStatus status = ReferenceStatus.ACTIVE;

    @Column(name = "embedding_version", nullable = false)
    private String embeddingVersion;

    @OneToMany(mappedBy = "referenceSet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FaceReferenceEmbedding> embeddings = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}