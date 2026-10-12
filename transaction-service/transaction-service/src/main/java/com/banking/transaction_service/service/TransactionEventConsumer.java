package com.banking.transaction_service.service;


import com.banking.transaction_service.entity.Transaction;
import com.banking.transaction_service.entity.TransactionStatus;
import com.banking.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionEventConsumer {
    private final TransactionRepository transactionRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionService transactionService;

    private static final long OTP_EXPIRY_MINUTES = 5;
    private static final String TRANSACTION_OTP_GENERATED_TOPIC = "transaction.otp.generated";

    /**
     * Consume verification.required
     * Generate OTP and ask user to verify
     * @param payload
     */
    @KafkaListener(topics = "verification.required", groupId = "transaction-service-group")
    // below method receives the payload from Kafka
    // listener annotation connects the method to the Kafka topic:
    public void consumeVerificationRequired(@Payload Map<String, Object> payload) {
        try {
            String transactionId = (String) payload.get("transactionId");
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");
            log.info("Verification required for transaction: {}  reason: {}", transactionId, reason);
         Transaction transaction = transactionRepository.findByTransactionId(transactionId)
                 .orElseThrow(() -> new RuntimeException("No transaction found for id: " + transactionId));
        // Add IG (Idempotency Guard) to prevent duplicate requests
          if(transaction.getTransactionStatus() != TransactionStatus.PROCESSING){
              log.warn("Transaction {} not PROCESSING - skipping", transactionId);
              return;
          }
              // Generate OTP (6-digit) and ask user to verify
              String otp = String.format("%06d", (int)(Math.random()*900000) + 100000);
             // Store OTP in redis - expires in 5 min
            String otpKey = "verification:otp:" + transactionId;
            redisTemplate.opsForValue().set(otpKey, otp, OTP_EXPIRY_MINUTES, TimeUnit.MINUTES); // OTP TTL (Expiration) : 5 minutes or 300 seconds
           // Update status (Initially its processing after OTP generation it's pending)
            transaction.setTransactionStatus(TransactionStatus.PENDING_VERIFICATION);
            transactionRepository.save(transaction);
            log.info("OTP generated for transaction: {} expires in  {} min", transactionId,OTP_EXPIRY_MINUTES);
           /*
              Notify user once my bank system generates OTP
              User gets OTP via SMS/mail
              Build the kafka event payload dynamically using HashMap
            */
            Map<String, Object> otpEvent = new HashMap<>();
            otpEvent.put("transactionId", transactionId);
            otpEvent.put("accountNumber", accountNumber);
            otpEvent.put("reason", reason);
            otpEvent.put("otp", otp);
            otpEvent.put("amount", payload.get("amount"));
            kafkaTemplate.send(TRANSACTION_OTP_GENERATED_TOPIC, transactionId, otpEvent);
            // above OTP_GENERATED event is consumed by our notification service

        } catch (Exception e) {
            log.error("Error  handling verification required: {}", e.getMessage());

        }
    }










}
