package com.htttdn.hrm.entity.enums;

public enum LeaveType {
    ANNUAL,
    SICK,
    MATERNITY,
    UNPAID,
    OTHER;

    /**
     * How the leave is paid when nobody with {@code request.manage} decides otherwise.
     * {@code OTHER} defaults to unpaid because it is the catch-all bucket: treating it as
     * employer-paid would hand every employee a way around the annual leave quota.
     */
    public LeaveSalaryTreatment defaultSalaryTreatment() {
        return switch (this) {
            case ANNUAL -> LeaveSalaryTreatment.EMPLOYER_PAID;
            case SICK, MATERNITY -> LeaveSalaryTreatment.SOCIAL_INSURANCE;
            case UNPAID, OTHER -> LeaveSalaryTreatment.UNPAID;
        };
    }
}
