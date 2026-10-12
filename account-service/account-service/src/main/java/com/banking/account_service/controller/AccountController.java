package com.banking.account_service.controller;
import com.banking.account_service.dto.AccountResponse;
import com.banking.account_service.dto.CreateAccountRequest;
import com.banking.account_service.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/accounts")
@Slf4j// used for log
@RequiredArgsConstructor

// add all endpoints here
/*
-> Create Account
-> getBalance()
-> blockAccount()
-> Saga S-I
-> Deduct balance
-> Credit balance (Credit receiver or Credit Sender (refund))
*/
public class AccountController {
    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);

    }
    // get accountNumber details
    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getBalance(@PathVariable String accountNumber){
        AccountResponse response = accountService.getAccount(accountNumber);
        return ResponseEntity.ok(response);

    }
    // get balance by accountNumber
    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> getBalanceByAccountNumber(@PathVariable String accountNumber){
        BigDecimal response = accountService.getBalance(accountNumber);
        return ResponseEntity.ok(response);
    }
    @PutMapping("/{accountNumber}/block")
    public ResponseEntity<String> blockAccount(@PathVariable String accountNumber) {
        accountService.blockAccount(accountNumber);
        return ResponseEntity.ok("Account blocked successfully");
    }
     /* Saga S-1 Deduct balance
     -> Called by transaction service when transfer is initiated
      */
    @PutMapping("/{accountNumber}/deduct")
    public ResponseEntity<String> deductBalance(@PathVariable String accountNumber, @RequestParam BigDecimal amount){
        accountService.deductBalance(accountNumber,amount);
        return ResponseEntity.ok("Balance deducted successfully");
    }
    /*
     * SAGA STEP 4 - Compensating transaction endpoint
     * CALLED BY TRANSACTION SERVICE in TWO SCENARIOS :
     * 1. Fraud detected -> refund sender (undo step-1)
     * 2. Transaction completed -> credit receiver
     */
    @PutMapping("/{accountNumber}/credit")
    public ResponseEntity<String> creditBalance(@PathVariable String accountNumber,@RequestParam BigDecimal amount) {
        accountService.creditBalance(accountNumber, amount);
                return ResponseEntity.ok("Balance credited successfully");
    }


}
