package ark_java_client;

import lombok.Data;

@Data
public class TransactionWrapper {
    private String status;
    private Transaction transaction;
}
