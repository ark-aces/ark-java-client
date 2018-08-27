package ark_java_client.example;

import ark_java_client.*;
import ark_java_client.lib.NiceObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class GetAddressTransactionsExample {
    
    private static final NiceObjectMapper niceObjectMapper = new NiceObjectMapper(new ObjectMapper());

    public static void main(String[] args) {
        ArkNetworkFactory arkNetworkFactory = new ArkNetworkFactory();
        ArkNetwork arkNetwork = arkNetworkFactory.createFromYml("mainnet.yml");

        HttpArkClientFactory httpArkClientFactory = new HttpArkClientFactory();
        ArkClient arkClient = httpArkClientFactory.create(arkNetwork);
        
        List<Transaction> transactions = arkClient.getTransactionByRecipientAddress("ARNJJruY6RcuYCXcwWsu4bx9kyZtntqeAx",10, 0);
        log.info(niceObjectMapper.writeValueAsString(transactions));
    }
}
