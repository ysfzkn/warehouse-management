package com.warehouse.dto.payment;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data @Builder
public class InstallmentQueryResult {
    private String binNumber;
    private String cardType;
    private String cardAssociation;
    private String cardFamily;
    private String bankName;
    private List<InstallmentOption> options;

    @Data @Builder
    public static class InstallmentOption {
        private int installmentCount;
        private BigDecimal installmentPrice;
        private BigDecimal totalPrice;
    }
}
