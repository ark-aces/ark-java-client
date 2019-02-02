package ark_java_client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

@Data
public class CreateArkV2TransactionsRequest {

    private List<JsonNode> transactions;

}
