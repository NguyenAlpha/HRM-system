package com.htttdn.hrm.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PayrollTaxBracketId implements Serializable {

    @Column(name = "tax_rule_id")
    private Long taxRuleId;

    @Column(name = "lower_bound", precision = 15, scale = 2)
    private BigDecimal lowerBound;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PayrollTaxBracketId that)) {
            return false;
        }
        return Objects.equals(taxRuleId, that.taxRuleId) && Objects.equals(lowerBound, that.lowerBound);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taxRuleId, lowerBound);
    }
}
