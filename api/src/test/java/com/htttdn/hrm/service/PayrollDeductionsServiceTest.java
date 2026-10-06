package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayrollDeductionsServiceTest {
    private final List<PayrollDeductionsService.TaxBracket> rules2026 = List.of(
        bracket("0", "10000000", "0.05"),
        bracket("10000000", "30000000", "0.10"),
        bracket("30000000", "60000000", "0.20"),
        bracket("60000000", "100000000", "0.30"),
        bracket("100000000", null, "0.35")
    );

    @Test
    void progressiveTaxUses2026MonthlyBracketsAtBoundaries() {
        assertEquals(new BigDecimal("0.00"), tax("0"));
        assertEquals(new BigDecimal("500000.00"), tax("10000000"));
        assertEquals(new BigDecimal("2500000.00"), tax("30000000"));
        assertEquals(new BigDecimal("8500000.00"), tax("60000000"));
        assertEquals(new BigDecimal("20500000.00"), tax("100000000"));
        assertEquals(new BigDecimal("24000000.00"), tax("110000000"));
    }

    private BigDecimal tax(String taxable) {
        return PayrollDeductionsService.progressiveTax(new BigDecimal(taxable), rules2026);
    }

    private PayrollDeductionsService.TaxBracket bracket(String lower, String upper, String rate) {
        return new PayrollDeductionsService.TaxBracket(new BigDecimal(lower),
            upper == null ? null : new BigDecimal(upper), new BigDecimal(rate));
    }
}
