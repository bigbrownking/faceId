package org.ktz.faceid.service.face;

import lombok.RequiredArgsConstructor;
import org.ktz.faceid.crypto.EnvelopeCrypto;
import org.ktz.faceid.domain.reference.*;
import org.ktz.faceid.repository.ReferenceEmbeddingRepository;
import org.ktz.faceid.repository.ReferenceSetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReferenceSetService {

    private final ReferenceSetRepository setRepo;
    private final ReferenceEmbeddingRepository embRepo;
    private final EnvelopeCrypto crypto;

    @Value("${face.embedding-version}")
    private String embeddingVersion;

    /** poseEmbeddings: pose name -> embedding. FRONT is stored as trusted. */
    @Transactional
    public FaceReferenceSet createActiveSet(Long userId, Map<String, float[]> poseEmbeddings) {
        // supersede any existing active set
        setRepo.findByUserIdAndStatus(userId, ReferenceStatus.ACTIVE).ifPresent(old -> {
            old.setStatus(ReferenceStatus.SUPERSEDED);
            setRepo.save(old);
        });

        FaceReferenceSet set = new FaceReferenceSet();
        set.setId(UUID.randomUUID());
        set.setUserId(userId);
        set.setStatus(ReferenceStatus.ACTIVE);
        set.setEmbeddingVersion(embeddingVersion);
        setRepo.save(set);

        for (Map.Entry<String, float[]> e : poseEmbeddings.entrySet()) {
            String pose = e.getKey();
            byte[] embBytes = EnvelopeCrypto.floatsToBytes(e.getValue());
            EnvelopeCrypto.Sealed sealed = crypto.seal(embBytes);

            FaceReferenceEmbedding emb = new FaceReferenceEmbedding();
            emb.setId(UUID.randomUUID());
            emb.setReferenceSet(set);
            emb.setPose(pose);
            emb.setTrusted("FRONT".equals(pose)); // FRONT is the trusted anchor
            emb.setEncryptedTemplate(sealed.ciphertext());
            emb.setEncryptedDataKey(sealed.wrappedDek());
            emb.setKmsKeyId(crypto.kmsKeyId());
            embRepo.save(emb);
        }
        return set;
    }

    @Transactional(readOnly = true)
    public Optional<FaceReferenceSet> activeSet(Long userId) {
        return setRepo.findByUserIdAndStatus(userId, ReferenceStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public boolean hasTrustedFront(Long userId) {
        return activeSet(userId)
                .flatMap(s -> embRepo.findByReferenceSetAndPose(s, "FRONT"))
                .map(FaceReferenceEmbedding::isTrusted)
                .orElse(false);
    }

    /** Decrypt the trusted FRONT embedding for matching. */
    @Transactional(readOnly = true)
    public float[] trustedFrontEmbedding(Long userId) {
        FaceReferenceSet set = activeSet(userId)
                .orElseThrow(() -> new IllegalStateException("No active reference set"));
        FaceReferenceEmbedding front = embRepo.findByReferenceSetAndPose(set, "FRONT")
                .orElseThrow(() -> new IllegalStateException("No FRONT reference"));
        byte[] raw = crypto.open(front.getEncryptedTemplate(), front.getEncryptedDataKey());
        return EnvelopeCrypto.bytesToFloats(raw);
    }
}