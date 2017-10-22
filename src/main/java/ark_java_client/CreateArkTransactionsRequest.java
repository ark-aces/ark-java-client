package ark_java_client;

import lombok.Data;

import java.util.List;

@Data
public class CreateArkTransactionsRequest {

    private List<CreateArkTransactionRequest> transactions;

}
