package org.ktz.faceid.domain.challenge;

public enum ChallengeMode {
    AUTH,    // HOLD_FRONT + one random turn (2 actions)
    ENROLL   // all three (HOLD_FRONT, TURN_LEFT, TURN_RIGHT)
}