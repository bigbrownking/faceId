package org.ktz.faceid.controller;

import lombok.RequiredArgsConstructor;
import org.ktz.faceid.domain.reference.ReferenceStatus;
import org.ktz.faceid.repository.ReferenceSetRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/face-id")
@RequiredArgsConstructor
public class EnrolledController {

    private final ReferenceSetRepository setRepo;

    @GetMapping("/enrolled/{userId}")
    public ResponseEntity<Map<String, Object>> enrolled(@PathVariable Long userId) {
        boolean enrolled = setRepo.existsByUserIdAndStatus(userId, ReferenceStatus.ACTIVE);
        return ResponseEntity.ok(Map.of("enrolled", enrolled));
    }
}