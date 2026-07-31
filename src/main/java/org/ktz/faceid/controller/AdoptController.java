package org.ktz.faceid.controller;

import lombok.RequiredArgsConstructor;
import org.ktz.faceid.service.face.ReferenceAdoptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/face-id")
@RequiredArgsConstructor
public class AdoptController {

    private final ReferenceAdoptionService adoptionService;

    /** Bind an anonymous reference-set job to a user after registration. */
    @PostMapping("/adopt")
    public ResponseEntity<Map<String, Object>> adopt(@RequestBody Map<String, String> body) {
        UUID jobId = UUID.fromString(body.get("jobId"));
        String jobToken = body.get("jobToken");
        Long userId = Long.parseLong(body.get("userId"));
        boolean ok = adoptionService.adopt(jobId, jobToken, userId);
        return ResponseEntity.ok(Map.of("adopted", ok));
    }
}