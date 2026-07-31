package org.ktz.faceid.service.challenge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ktz.faceid.config.ChallengeProperties;
import org.ktz.faceid.domain.challenge.*;
import org.ktz.faceid.repository.ChallengeRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(ChallengeProperties.class)
public class ChallengeService {

    private final ChallengeRepository repo;
    private final ChallengeProperties props;
    private final SecureRandom rnd = new SecureRandom();

    /** Create a challenge. For AUTH: FRONT + one random turn. For ENROLL: all three. */
    @Transactional
    public Challenge create(ChallengeMode mode, Long userId) {
        List<PoseAction> actions = (mode == ChallengeMode.AUTH)
                ? List.of(PoseAction.HOLD_FRONT, randomTurn())
                : List.of(PoseAction.HOLD_FRONT, PoseAction.TURN_LEFT, PoseAction.TURN_RIGHT);

        Challenge c = new Challenge();
        c.setChallengeId(UUID.randomUUID());
        c.setMode(mode);
        c.setActions(actions.stream().map(Enum::name).toList());
        c.setUserId(userId);
        c.setAttemptsRemaining(props.getMaxAttempts());
        c.setExpiresAt(Instant.now().plus(Duration.ofSeconds(props.getTtlSeconds())));
        Challenge saved = repo.save(c);
        log.info("Challenge {} created mode={} actions={} user={}",
                saved.getChallengeId(), mode, saved.getActions(), userId);
        return saved;
    }

    /**
     * Load a challenge for a verify/enroll attempt and validate it's usable.
     * Decrements attemptsRemaining. Throws with doc-aligned messages.
     */
    @Transactional
    public Challenge consumeAttempt(UUID challengeId) {
        Challenge c = repo.findById(challengeId)
                .orElseThrow(() -> new ChallengeException(409, "Face challenge not found"));

        if (c.isConsumed()) {
            throw new ChallengeException(409, "Face challenge already used");
        }
        if (Instant.now().isAfter(c.getExpiresAt())) {
            throw new ChallengeException(409, "Face challenge expired");
        }
        if (c.getAttemptsRemaining() <= 0) {
            throw new ChallengeException(429, "Face verification attempts are exhausted");
        }
        c.setAttemptsRemaining(c.getAttemptsRemaining() - 1);
        return repo.save(c);
    }

    /** Mark challenge fully consumed after a successful verify/enroll. */
    @Transactional
    public void markConsumed(UUID challengeId) {
        repo.findById(challengeId).ifPresent(c -> {
            c.setConsumed(true);
            repo.save(c);
        });
    }

    /** Verify the actions the client submitted match the issued challenge (order matters). */
    public void validateActions(Challenge c, List<String> submittedActions) {
        if (submittedActions == null || submittedActions.size() != c.getActions().size()) {
            throw new ChallengeException(400, "Submitted actions do not match challenge");
        }
        for (int i = 0; i < c.getActions().size(); i++) {
            if (!c.getActions().get(i).equals(submittedActions.get(i))) {
                throw new ChallengeException(400,
                        "Action mismatch at position " + i +
                                ": expected " + c.getActions().get(i));
            }
        }
    }

    private PoseAction randomTurn() {
        return rnd.nextBoolean() ? PoseAction.TURN_LEFT : PoseAction.TURN_RIGHT;
    }
}