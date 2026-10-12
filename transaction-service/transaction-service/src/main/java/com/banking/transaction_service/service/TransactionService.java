package com.banking.transaction_service.service;
import com.banking.transaction_service.client.AccountServiceClient;
import com.banking.transaction_service.dto.TransactionResponse;
import com.banking.transaction_service.dto.TransferRequest;
import com.banking.transaction_service.entity.Transaction;
import com.banking.transaction_service.entity.TransactionStatus;
import com.banking.transaction_service.entity.TransactionType;
import com.banking.transaction_service.event.TransactionInitiatedEvent;
import com.banking.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    //create kafka topic variables (txn initiated, txn completed, txn refunded)
    private static final String TRANSACTION_INITIATED_TOPIC = "txn-initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "txn-completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "txn-refunded";
    /*
    * SAGA STEP 1 - Initiate transfer
    * Deducts from sender via feignClient
    * Saves transaction as PROCESSING
    * Publish event to kafka for fraud check
    * Returns.
    * @param request
    * @return
     */
    public TransactionResponse transfer(TransferRequest request) {
       log.info("SAGA START - Transfer : {} -> amount: {}",
               request.getSenderAccountNumber(),
               request.getReceiverAccountNumber(),
               request.getAmount());

       // SAGA STEP 1: Deduct from sender
        accountServiceClient.deductBalance(
                request.getSenderAccountNumber(),
                request.getAmount()
        );
        // Map DTO -> Entity and save to Repository
        Transaction transaction = Transaction.builder()
                .senderAccountNumber(request.getSenderAccountNumber())
                .receiverAccountNumber(request.getReceiverAccountNumber())
                .description(request.getDescription())
                .amount(request.getAmount())
                .transactionType(TransactionType.TRANSFER)
                .transactionStatus(TransactionStatus.PROCESSING)
                .description(request.getDescription())
                .referenceNumber(UUID.randomUUID().toString())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved as PROCESSING: {} ", savedTransaction.getTransactionId());
        // Publish event to kafka
        //SAGA STEP 2: Publish for fraud check
        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                savedTransaction.getTransactionId(),
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getReceiverAccountNumber(),
                savedTransaction.getAmount(),
                savedTransaction.getDescription()
        );
        log.info("Event published to kafka: {}", event);
        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC, savedTransaction.getTransactionId(), event);
        log.info("SAGA STEP 2 - TransactionInitiatedEvent published: {}", savedTransaction.getTransactionId());
        return mapToResponse(savedTransaction);

    }
    // Entity -> Response DTO
    private TransactionResponse mapToResponse(Transaction savedTransaction) {
        TransactionResponse transactionResponse = TransactionResponse.builder()
                .id(savedTransaction.getTransactionId())
                .senderAccountNumber(savedTransaction.getSenderAccountNumber())
                .receiverAccountNumber(savedTransaction.getReceiverAccountNumber())
                .amount(savedTransaction.getAmount())
                .transactionType(savedTransaction.getTransactionType() != null
                        ? savedTransaction.getTransactionType().name()
                        : null)
                .transactionStatus(savedTransaction.getTransactionStatus() != null
                        ? savedTransaction.getTransactionStatus().name()
                        : null)
                .description(savedTransaction.getDescription())
                .failureReason(savedTransaction.getFailureReason())
                .referenceNumber(savedTransaction.getReferenceNumber())
                .createdAt(savedTransaction.getCreatedAt())
                .completedAt(savedTransaction.getCompletedAt())
                .build();

        return transactionResponse;
    }
    // Logic -> for fetching transaction by ID
    public TransactionResponse getTransactionById(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found :  " + transactionId));
        return mapToResponse(transaction);
    }
    // Logic -> get all Transactions
    public List<TransactionResponse> getTransactionHistory(String accountNumber){
         List<Transaction> response = transactionRepository.findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber);
         // convert list of Transaction entities into a list of TransactionResponse DTOs
         return response.stream() // process each Transaction in the list
                 .map(this::mapToResponse) // calls mapToResponse(transaction) for every item, converting it to TransactionResponse
                 .toList();   // collects all converted responses into a List<TransactionResponse>
    }// .map(this::mapToResponse) => .map(transaction -> mapToResponse(transaction))


    public TransactionResponse verifyOtp(String transactionId, String otp) {
        log.info("OTP verification for the transaction: {}", transactionId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found :  " + transactionId));
        // get the stored otp and compare it with user entered otp
    }
}
