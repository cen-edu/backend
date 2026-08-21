package com.cenedu.backend.domain.problem.authoring.repair;

/** 부분 수정이 허용되는 Snapshot 구성요소다. */
public enum RepairTarget {
    CONTENT,
    CHOICES,
    ANSWERS,
    STEPS,
    EXPLANATION,
    RUBRIC,
    LEARNING_GUIDE,
    ASSET
}
