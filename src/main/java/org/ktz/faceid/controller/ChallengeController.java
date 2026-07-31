package org.ktz.faceid.controller;

import lombok.RequiredArgsConstructor;
import org.ktz.faceid.domain.challenge.*;
import org.ktz.faceid.dto.challenge.ChallengeDtos.*;
import org.ktz.faceid.service.challenge.ChallengeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/face-id/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    @PostMapping
    public ResponseEntity<ChallengeResponse> create(
            @RequestBody CreateChallengeRequest req,
            @RequestAttribute(value = "userId", required = false) Long userId) {

        ChallengeMode mode = ChallengeMode.valueOf(req.mode().toUpperCase());
        Challenge c = challengeService.create(mode, userId);

        return ResponseEntity.ok(new ChallengeResponse(
                c.getChallengeId(),
                c.getMode().name(),
                c.getActions(),
                c.getExpiresAt(),
                c.getAttemptsRemaining()
        ));
    }
}