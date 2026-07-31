package org.ktz.faceid.dto.job;

import org.ktz.faceid.domain.job.FaceJob;

/**
 * Envelope sent over Redis to the orchestrator.
 * Routing fields (userId / jobToken) tell the orchestrator which client to push to.
 * The `job` field is the same JobView the client polls.
 */
public record JobEventMessage(
        Long userId,        // null for anonymous registration
        String jobToken,    // set for anonymous jobs
        JobView job
) {
    public static JobEventMessage from(FaceJob j) {
        return new JobEventMessage(j.getUserId(), j.getJobToken(), JobView.from(j));
    }
}