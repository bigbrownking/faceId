//package org.ktz.faceid.service;
//
//import lombok.extern.slf4j.Slf4j;
//import org.ktz.faceid.storage.CaptureStorage;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.Instant;
//import java.util.List;
//
///**
// * Enforces capture retention. MinIO lifecycle rules are the primary mechanism,
// * this job is the application-level backstop: it deletes objects whose
// * capture_retention_until has passed and clears the pointer in PostgreSQL.
// */
//@Slf4j
//@Service
//public class RetentionJob {
//    private final TransactionRepository txnRepo;
//    private final CaptureStorage storage;
//
//    public RetentionJob(TransactionRepository txnRepo, CaptureStorage storage) {
//        this.txnRepo = txnRepo;
//        this.storage = storage;
//    }
//
//    @Scheduled(cron = "0 0 * * * *")
//    @Transactional
//    public void purgeExpiredCaptures() {
//        List<FaceAuthTransaction> expired =
//                txnRepo.findByCaptureRetentionUntilBeforeAndFrontCaptureObjectKeyIsNotNull(Instant.now());
//        for (FaceAuthTransaction t : expired) {
//            storage.delete(t.getFrontCaptureObjectKey());
//            t.setFrontCaptureObjectKey(null);
//            txnRepo.save(t);
//        }
//        if (!expired.isEmpty()) log.info("Purged {} expired captures", expired.size());
//    }
//}
