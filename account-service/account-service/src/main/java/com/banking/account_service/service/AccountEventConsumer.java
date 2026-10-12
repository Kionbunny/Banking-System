package com.banking.account_service.service;
// Transaction service produce event txn-initiated that event is consumed by
// AccountEventConsumer
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Map;
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEventConsumer {
    private final AccountService accountService;
    /**
     * Consume transaction completed event from a kafka
     * Credit -> receiver account
     * @param payload
     */
   @KafkaListener(topics = "transaction.completed")
  public void consumeTransactionCompleted(@Payload Map<String, Object> payload) {
   try {
       String receiverAccount = (String) payload.get("receiverAccountNumber");
       BigDecimal amount = new BigDecimal(payload.get("amount").toString());
       log.info("Transaction completed for receiver account: {} with amount: {}", receiverAccount, amount);
      accountService.creditBalance(receiverAccount, amount);
   } catch (Exception e) {
       log.error("Error processing transaction completed event: {}", e.getMessage());
       e.printStackTrace();
   }
  }
  /*
    * Consume fraud.detected event from kafka
    * Blocks the flagged account
    * @param payload
   */
    public void consumeFraudDetected(@Payload Map<String, Object> payload) {
       try {
           String accountNumber = (String) payload.get("accountNumber");
           log.info("Fraud detected - blocking account: {}", accountNumber);
           accountService.blockAccount(accountNumber);
       }catch (Exception e) {
           log.error("Error blocking account : {}", e.getMessage());
       }

    }
}
// transactionCompleted() -> No fraud detected & Amount credited to receiver
