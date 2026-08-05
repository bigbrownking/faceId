package org.ktz.faceid.domain.job;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "face_jobs", indexes = {
        @Index(name = "idx_face_jobs_status", columnList = "status"),
        @Index(name = "idx_face_jobs_user", columnList = "user_id")
})
public class FaceJob {

    @Id
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "job_token")
    private String jobToken;

    @Column(name = "challenge_id")
    private UUID challengeId;

    private int eventVersion = 1;

    @Column(name = "progress_total")
    private int progressTotal;
    @Column(name = "progress_done")
    private int progressDone;
    @Column(name = "progress_success")
    private int progressSuccess;
    @Column(name = "progress_failed")
    private int progressFailed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "finished_at")
    private Instant finishedAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
}