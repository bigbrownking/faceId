package org.ktz.faceid.controller;

import lombok.RequiredArgsConstructor;
import org.ktz.faceid.service.face.FaceIdResetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/face-id")
@RequiredArgsConstructor
public class FaceResetController {

    private final FaceIdResetService resetService;

    /** DELETE /face-id/enrolled/{userId} — remove all Face ID data for the user. */
    @DeleteMapping("/enrolled/{userId}")
    public ResponseEntity<Map<String, Object>> reset(@PathVariable Long userId) {
        FaceIdResetService.Result r = resetService.reset(userId);
        return ResponseEntity.ok(Map.of(
                "reset", true,
                "userId", userId,
                "referenceSets", r.referenceSets(),
                "embeddings", r.embeddings(),
                "referenceCaptures", r.referenceCaptures(),
                "objectsDeleted", r.objectsDeleted()
        ));
    }
}