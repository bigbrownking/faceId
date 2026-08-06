package org.ktz.faceid.controller;

import lombok.RequiredArgsConstructor;
import org.ktz.faceid.crypto.EnvelopeCrypto;
import org.ktz.faceid.domain.capture.FaceCapture;
import org.ktz.faceid.repository.FaceCaptureRepository;
import org.ktz.faceid.storage.CaptureStorage;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Monitoring/admin access to stored captures.
 *
 * IMPORTANT: this endpoint returns decrypted biometric photos. It MUST be locked
 * down to admin/monitoring roles at the orchestrator/gateway layer (FaceAuth itself
 * trusts the orchestrator on 127.0.0.1). Do NOT expose it publicly.
 */
@RestController
@RequestMapping("/face-id/captures")
@RequiredArgsConstructor
public class CaptureMonitoringController {

    private final FaceCaptureRepository repo;
    private final CaptureStorage storage;
    private final EnvelopeCrypto crypto;

    /** List a user's captures (metadata only). type = REFERENCE | ATTEMPT */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Map<String, Object>>> list(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "ATTEMPT") String type) {
        List<FaceCapture> items = repo.findByUserIdAndCaptureTypeOrderByCapturedAtDesc(userId, type);
        List<Map<String, Object>> out = items.stream().map(c -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", c.getId().toString());
            m.put("jobId", c.getJobId() == null ? null : c.getJobId().toString());
            m.put("pose", c.getPose());
            m.put("type", c.getCaptureType());
            m.put("objectKey", c.getObjectKey());
            m.put("sha256", c.getSha256());
            m.put("encrypted", c.isEncrypted());
            m.put("capturedAt", c.getCapturedAt().toString());
            m.put("retentionUntil", c.getRetentionUntil().toString());
            return m;
        }).toList();
        return ResponseEntity.ok(out);
    }

    /** Return the actual JPEG for one capture (decrypts if stored encrypted). */
    @GetMapping(value = "/{captureId}/image", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable UUID captureId) {
        FaceCapture c = repo.findById(captureId).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();

        byte[] stored = storage.getBytes(c.getObjectKey());
        byte[] jpeg = c.isEncrypted() ? crypto.openBlob(stored) : stored;

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_JPEG)
                .body(jpeg);
    }
}