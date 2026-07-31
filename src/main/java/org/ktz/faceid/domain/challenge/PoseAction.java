package org.ktz.faceid.domain.challenge;

public enum PoseAction {
    HOLD_FRONT,
    TURN_LEFT,
    TURN_RIGHT;

    public String toPoseName() {
        return switch (this) {
            case HOLD_FRONT -> "FRONT";
            case TURN_LEFT  -> "YAW_LEFT";
            case TURN_RIGHT -> "YAW_RIGHT";
        };
    }
}