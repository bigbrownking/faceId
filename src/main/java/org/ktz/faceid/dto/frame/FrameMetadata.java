package org.ktz.faceid.dto.frame;

import java.util.List;
import java.util.UUID;

public record FrameMetadata(
        UUID challengeId,
        List<FrameSpec> frames
) {
    public record FrameSpec(int ordinal, String requestedAction) {}
}