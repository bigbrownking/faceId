package org.ktz.faceid.domain.challenge;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "face_challenges", indexes = {
        @Index(name = "idx_challenge_user", columnList = "user_id")
})
public class Challenge {

    @Id
    private UUID challengeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChallengeMode mode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> actions;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "attempts_remaining", nullable = false)
    private int attemptsRemaining;

    @Column(nullable = false)
    private boolean consumed = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}