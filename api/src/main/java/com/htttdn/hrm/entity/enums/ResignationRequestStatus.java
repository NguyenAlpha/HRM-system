package com.htttdn.hrm.entity.enums;

/** APPROVED stays until the last working day has passed; the request then records when employment ended. */
public enum ResignationRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
