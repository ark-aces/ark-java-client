package ark_java_client;

import java.util.List;

public interface ArkClient {
    void updatePeers();
    List<Transaction> getTransactions(Integer offset);
    List<Transaction> getTransactionByRecipientAddress(String recipientAddress);
    Transaction getTransaction(String arkTransactionId);
    String createTransaction(String recipientId, Long satoshiAmount, String vendorField, String passphrase);
    AccountBalance getBalance(String address);
}