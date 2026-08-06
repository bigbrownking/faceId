package org.ktz.faceid.domain.capture;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "face_capture", indexes = {
        @Index(name = "idx_capture_retention", columnList = "retention_until"),
        @Index(name = "idx_capture_user", columnList = "user_id, capture_type")
})
public class FaceCapture {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "pose")
    private String pose;

    @Column(name = "capture_type", nullable = false)
    private String captureType;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "encrypted", nullable = false)
    private boolean encrypted;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "retention_until", nullable = false)
    private Instant retentionUntil;
}