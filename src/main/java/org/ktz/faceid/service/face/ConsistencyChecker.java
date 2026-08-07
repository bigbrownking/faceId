package org.ktz.faceid.service.face;

import org.ktz.faceid.onnx.FaceEngine;
import org.ktz.faceid.service.job.JobRejectedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConsistencyChecker {

    // frames of the same person should be reasonably similar even across poses
    @Value("${face.consistency-min-cosine:0.28}")
    private double minCosine;

    public void ensureSamePerson(List<float[]> embeddings) {
        for (int i = 0; i < embeddings.size(); i++) {
            for (int j = i + 1; j < embeddings.size(); j++) {
                float sim = FaceEngine.cosine(embeddings.get(i), embeddings.get(j));
                if (sim < minCosine) {
                    throw new JobRejectedException("INCONSISTENT_FACES",
                            "Frames appear to be different people (sim=" + sim + ")");
                }
            }
        }
    }
}