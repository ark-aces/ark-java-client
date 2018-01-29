package ark_java_client;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

public class HttpArkClientFactory {

    public HttpArkClient create(ArkNetwork arkNetwork) {
        // Set up http client with sensible timeout values
        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(15000)
                .setReadTimeout(15000)
                .build();

        HttpArkClient httpArkClient = new HttpArkClient(arkNetwork, restTemplate);
        httpArkClient.updatePeers();

        return httpArkClient;
    }

}
