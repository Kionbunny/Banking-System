package com.banking.account_service.service;
import com.banking.account_service.dto.AccountResponse;
import com.banking.account_service.dto.CreateAccountRequest;
import com.banking.account_service.entity.Account;
import com.banking.account_service.entity.AccountStatus;
import com.banking.account_service.entity.AccountType;
import com.banking.account_service.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.security.SecureRandom;
// All money movement logic is implemented here
// deduct balance, credit, receive, refund
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private static final SecureRandom RANDOM = new SecureRandom();

    public AccountResponse createAccount(CreateAccountRequest request) {
    log.info("Creating account for {}", request.getAccountHolderName());

    if(accountRepository.existsByAccountHolderName(request.getAccountHolderName())){
        throw new RuntimeException("Account holder name already exists" + request.getAccountHolderName());
    }

    // get DTO and map it to Entity, create an account object and save it to repository
        // Request DTO -> Entity
    Account account = Account.builder()
            .accountHolderName(request.getAccountHolderName())
            .email(request.getEmail())
            .phone(request.getPhone())
            .accountType(request.getAccountType())
            .accountStatus(AccountStatus.ACTIVE)
            .balance(request.getInitialDeposit())
            .accountNumber(generateAccountNumber())
            .dailyTransactionLimit(request.getAccountType() == AccountType.SAVINGS
                    ? new BigDecimal("100000") : new BigDecimal("200000"))
                   .build();
       // save the account obj to repo
        Account savedAccount = accountRepository.save(account);
        log.info("Account created: {}", savedAccount.getAccountNumber());
        // return account response
        // Entity -> Response DTO
        return mapToResponse(savedAccount);
    }
    /* Get account by accountNumber
     * @param accountNumber
     * @return
     */
    public AccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        return mapToResponse(account);
    }
    /* Get account balance
    *  @param accountNumber
    * @return
    */
    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        return account.getBalance();
    }
    /** block account by accountNumber (Called by fraud detection svc via Kafka)
     * @param accountNumber
     * @return
     */
    public void blockAccount(String accountNumber) {
        log.info("Blocking account: {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        account.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked: {}", accountNumber);
    }
    /* SAGA Step-1
     * Deduct balance from sender account
     * @param accountNumber
     * @param amount
     */
    public void deductBalance(String accountNumber, BigDecimal amount) {
        log.info("Deducting balance from account: {} for amount: {}", accountNumber, amount);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if(account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Account is not active" + accountNumber);
        }
        if(account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance" + accountNumber);
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Amount Deducted. New  Balance: {}", account.getBalance());
    }
    /**
     * Credit balance
     * Called by Transaction service via Kafka
     * @param accountNumber
     * @param amount
     */
    public void creditBalance(String accountNumber, BigDecimal amount) {
        log.info("Crediting balance to account: {} for amount: {}", accountNumber, amount);
      Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("Balance updated. New  Balance: {}", account.getBalance());
    }

    // Generate 12-digit account number
    private String generateAccountNumber() {
        String accountNumber;
        do { // generate an arr[] of random number
            long number = RANDOM.nextLong(1_000_000_000_000L);
           accountNumber = String.format("%012d", number);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
    // map the Entity to Response
    private AccountResponse mapToResponse(Account account) {
        AccountResponse response = AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountHolderName(account.getAccountHolderName())
                .email(account.getEmail())
                .phone(account.getPhone())
                .accountType(account.getAccountType())
                .status(account.getAccountStatus())
                .balance(account.getBalance())
                .dailyTransactionLimit(account.getDailyTransactionLimit())
                .createdAt(account.getCreatedAt())
                .build();
        return response;
    }



}
