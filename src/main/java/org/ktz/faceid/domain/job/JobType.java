package org.ktz.faceid.domain.job;

public enum JobType {
    PREPARE_REFERENCE_SET,   // регистрация: подготовка reference-эмбеддингов (аноним)
    ENROLL_REFERENCE_SET,    // дозаливка недостающих поз (требует trusted FRONT)
    VERIFY                   // проверка лица при логине
}