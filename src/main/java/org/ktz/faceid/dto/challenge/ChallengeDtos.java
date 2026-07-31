package org.ktz.faceid.dto.challenge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ChallengeDtos {

    public record CreateChallengeRequest(String mode) {}   // "AUTH" | "ENROLL"

    public record ChallengeResponse(
            UUID challengeId,
            String mode,
            List<String> actions,
            Instant expiresAt,
            int attemptsRemaining
    ) {}
}