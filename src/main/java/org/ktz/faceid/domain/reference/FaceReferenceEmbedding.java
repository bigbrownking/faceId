package org.ktz.faceid.domain.reference;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "face_reference_embedding", indexes = {
        @Index(name = "idx_refemb_set", columnList = "reference_set_id"),
        @Index(name = "idx_refemb_pose", columnList = "reference_set_id, pose")
})
public class FaceReferenceEmbedding {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_set_id", nullable = false)
    private FaceReferenceSet referenceSet;

    @Column(nullable = false)
    private String pose;

    @Column(nullable = false)
    private boolean trusted;

    @Column(name = "encrypted_template", nullable = false)
    private byte[] encryptedTemplate;

    @Column(name = "encrypted_data_key", nullable = false)
    private byte[] encryptedDataKey;

    @Column(name = "kms_key_id", nullable = false)
    private String kmsKeyId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}