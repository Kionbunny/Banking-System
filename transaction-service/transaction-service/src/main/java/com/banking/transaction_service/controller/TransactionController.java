package com.banking.transaction_service.controller;


import com.banking.transaction_service.dto.TransactionResponse;
import com.banking.transaction_service.dto.TransferRequest;
import com.banking.transaction_service.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@Slf4j
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;
    // Create transaction
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request) {
        TransactionResponse response = transactionService.transfer(request);
        return ResponseEntity.status(HttpStatus.CREATED) // 201 Created
                             .body(response);

    }
    //Get transaction by ID
    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable String transactionId) {
        TransactionResponse response = transactionService.getTransactionById(transactionId);
        return ResponseEntity.ok(response); // 200 OK

    }
    // Get all transactions by user accountNumber
    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<List<TransactionResponse>> getTransactionHistory(@PathVariable String accountNumber) {
        List<TransactionResponse> response = transactionService.getTransactionHistory(accountNumber);
        return ResponseEntity.ok(response);
    }
    // OTP Verification end-point
    //POST /api/v1/transactions/123456/verify-otp?otp=4513
    @PostMapping("/{transactionId}/verify-otp")
   public ResponseEntity<TransactionResponse> verifyOtp(@PathVariable String transactionId, @RequestParam String otp) {
     log.info("OTP verification request - transactionId: {}, OTP: {}", transactionId, otp);
      TransactionResponse response = transactionService.verifyOtp(transactionId, otp);
      return ResponseEntity.ok(response);
   }





}
