package com.banking.transaction_service.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionResponse {
    private String id;
    private String senderAccountNumber;
    private String receiverAccountNumber;
    private BigDecimal amount;
    private String transactionType;
    private String transactionStatus;
    private String description;
    private String failureReason;
    private String referenceNumber;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
