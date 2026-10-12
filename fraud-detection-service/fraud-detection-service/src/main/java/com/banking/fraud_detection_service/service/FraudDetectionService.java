package com.banking.fraud_detection_service.service;


import com.banking.fraud_detection_service.client.AccountServiceClient;
import com.banking.fraud_detection_service.model.FraudCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionService {

    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${fraud.max-transactions-per-minute}")
    private int maxTransactionsPerMinute;


    // create kafka topic
    private static final String VERIFICATION_REQUIRED  = "verification.required";
    private static final String FRAUD_CHECK_CLEAN_RESULT_TOPIC = "fraud.check.clean.result";

    public void checkTransaction(Map<String, Object> payload) {
        String transactionId = (String) payload.get("transactionId");
        String accountNumber = (String) payload.get("accountNumber");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());

        // Fetch real balance from Account Service
        BigDecimal senderBalance = accountServiceClient.getBalance(accountNumber);
        log.info("Checking transaction : {} account : {} amount : {} balance : {}", transactionId, accountNumber, amount, senderBalance);

        FraudCheckResult result = performFraudChecks(accountNumber, amount, senderBalance);

        if(result.isFraud()) {
            log.info("Supicious activity detected - account : {} " +
                    "reason: {} - requesting OTP verification ", accountNumber, result.getReason());
            // Suspicious activity detected - publish events to kafka (Verification event)
            // transaction svc consumes this event and generates OTP
            //HashMap is being used to build the Kafka event payload dynamically before publishing it
            Map<String, Object> verificationEvent = new HashMap<>();
            verificationEvent.put("transactionId", transactionId);
            verificationEvent.put("accountNumber", accountNumber);
            verificationEvent.put("amount", amount);
            verificationEvent.put("reason", result.getReason());
            kafkaTemplate.send(VERIFICATION_REQUIRED, verificationEvent);
           /* {
                "transactionId": "TXN-1001",
                    "accountNumber": "52349825",
                    "amount": 500.00,
                    "reason": "Large transaction"
            } */
        } else {
            // Transaction is clean
            log.info("Transaction is safe - proceeding with transaction");
            Map<String,Object> transactionCleanEvent = new HashMap<>();
            transactionCleanEvent.put("transactionId", transactionId);
            transactionCleanEvent.put("isFraud", false);
            transactionCleanEvent.put("reason", null);
            kafkaTemplate.send(FRAUD_CHECK_CLEAN_RESULT_TOPIC, transactionId, transactionCleanEvent);
        }
    }
    // perform fraud checks (3 patterns) velocity check
    private FraudCheckResult performFraudChecks(
            String accountNumber,
            BigDecimal amount,
            BigDecimal senderBalance) {
        // Pattern 1: Velocity check (5 transactions in 60 sec)
        if(isVelocityExceeded(accountNumber)){
            return new FraudCheckResult(true, "Too many transactions in 60 seconds" + "- Velocity limit reached");
        }
        // Pattern 2: Amount check
        // if user makes (3-5)x of his avg transaction
//        if(isAmountSuspicious(accountNumber,amount)){
//            return new FraudCheckResult(true, "Unusual transaction amount" + "- exceeds 3x your average ");
//        }
        // Pattern 3: Balance check
        //It flags a transaction when the sender’s balance is less than 90% of the requested amount.
        if(senderBalance.compareTo(amount.multiply(new BigDecimal("0.9"))) < 0){
         return new FraudCheckResult(true, "Transaction amount exceeds 90% of balance");
        }
        // if all fraud checks failed (then transaction is clean)
        return new FraudCheckResult(false, null);
    }
    private boolean isVelocityExceeded(String accountNumber) {
        String key = "fraud:velocity" + accountNumber;
        // below line automatically increments a Redis counter by 1 :
        Long count = redisTemplate.opsForValue().increment(key);
        if(count != null && count == 1) {
            redisTemplate.expire(key,60, TimeUnit.SECONDS);
        }
        log.info("Velocity check - account: {} count: {}/{}",accountNumber,count,maxTransactionsPerMinute);
        return count != null && count > maxTransactionsPerMinute;
    }
    // get the avg and find if 3x (x = avg) then suspicious is detected



}
