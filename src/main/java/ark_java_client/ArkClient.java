package ark_java_client;

import java.util.List;

public interface ArkClient {
    List<Transaction> getTransactions(Integer limit, Integer offset);
    List<Transaction> getTransactionByRecipientAddress(String recipientAddress, Integer limit, Integer offset);
    Transaction getTransaction(String arkTransactionId);
    String broadcastTransaction(String recipientId, Long satoshiAmount, String vendorField, String passphrase, Integer nodes);
    AccountBalance getBalance(String address);
    String getAddress(String passphrase);
}