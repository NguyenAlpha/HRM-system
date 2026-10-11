package com.htttdn.hrm.entity.enums;

/** Manual payroll line for one employee in one period. */
public enum PayrollAdjustmentType {
    /** Bonus; taxable income. */
    BONUS,
    /** Other allowance that is taxable income. */
    TAXABLE_ALLOWANCE,
    /** Other allowance exempt from personal income tax. */
    NON_TAXABLE_ALLOWANCE,
    /** Deducted from net pay after tax, such as a salary advance or a fine. */
    DEDUCTION;

    public boolean isEarning() {
        return this != DEDUCTION;
    }
}
