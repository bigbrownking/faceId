package org.ktz.faceid.repository;

import org.ktz.faceid.domain.capture.FaceCapture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FaceCaptureRepository extends JpaRepository<FaceCapture, UUID> {
    List<FaceCapture> findByRetentionUntilBeforeAndCaptureTypeNot(Instant now, String captureType);
    List<FaceCapture> findByUserIdAndCaptureTypeOrderByCapturedAtDesc(Long userId, String captureType);
    List<FaceCapture> findByUserIdAndCaptureType(Long userId, String captureType);
}