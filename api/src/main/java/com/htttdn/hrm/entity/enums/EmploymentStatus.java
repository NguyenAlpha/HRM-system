package com.htttdn.hrm.entity.enums;

public enum EmploymentStatus {
    PROBATION,
    ACTIVE,
    RESIGNED,
    TERMINATED,
    RETIRED;

    /** Employment has ended: resigned, terminated or retired. */
    public boolean isEnded() {
        return this == RESIGNED || this == TERMINATED || this == RETIRED;
    }
}
