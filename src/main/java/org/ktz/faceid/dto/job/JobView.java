package org.ktz.faceid.dto.job;

import org.ktz.faceid.domain.job.FaceJob;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record JobView(
        UUID jobId,
        String type,
        String status,
        int eventVersion,
        Progress progress,
        Map<String, Object> result,
        ErrorInfo error,
        Instant createdAt,
        Instant finishedAt,
        Instant expiresAt
) {
    public record Progress(int total, int done, int success, int failed) {}
    public record ErrorInfo(String code, String message) {}

    public static JobView from(FaceJob j) {
        ErrorInfo err = (j.getErrorCode() == null && j.getErrorMessage() == null)
                ? null : new ErrorInfo(j.getErrorCode(), j.getErrorMessage());
        return new JobView(
                j.getJobId(),
                j.getType().name(),
                j.getStatus().name(),
                j.getEventVersion(),
                new Progress(j.getProgressTotal(), j.getProgressDone(),
                        j.getProgressSuccess(), j.getProgressFailed()),
                j.getResult(),
                err,
                j.getCreatedAt(),
                j.getFinishedAt(),
                j.getExpiresAt()
        );
    }
}