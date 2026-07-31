package org.ktz.faceid.config;

public enum LivenessMode {
    SHADOW,    // логируем, не блокируем
    ENFORCE    // требуем livenessPassed=true
}