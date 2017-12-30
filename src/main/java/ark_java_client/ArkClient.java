package ark_java_client;

import java.util.List;

public interface ArkClient {
    void updatePeers();
    List<Transaction> getTransactions(Integer limit, Integer offset);
    List<Transaction> getTransactionByRecipientAddress(String recipientAddress, Integer limit, Integer offset);
    Transaction getTransaction(String arkTransactionId);
    String broadcastTransaction(String recipientId, Long satoshiAmount, String vendorField, String passphrase);
    AccountBalance getBalance(String address);
}