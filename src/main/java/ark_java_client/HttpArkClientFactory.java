package ark_java_client;

import ark_java_client.lib.NiceObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class HttpArkClientFactory {

    private static final Integer DEFAULT_P2P_PORT = 4001;
    private static final Integer DEFAULT_API_PORT = 4003;

    public HttpArkClient create(ArkNetwork arkNetwork) {
        // Set up http client with sensible timeout values
        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(3000)
                .setReadTimeout(3000)
                .build();

        List<Peer> trustedPeers = arkNetwork.getTrustedPeerSettings().stream()
                .map(trustedPeerSetting -> {
                    Peer peer = new Peer();
                    peer.setIp(trustedPeerSetting.getHostname());
                    peer.setP2pPort(trustedPeerSetting.getP2pPort() != null ? trustedPeerSetting.getP2pPort() : DEFAULT_P2P_PORT);
                    peer.setApiPort(trustedPeerSetting.getPort() != null ? trustedPeerSetting.getPort() : DEFAULT_API_PORT);
                    return peer;
                })
                .collect(Collectors.toList());

        if (trustedPeers.size() < 1) {
            throw new RuntimeException("No trusted peers available to connect to ark network");
        }

        log.info("Using Ark network trusted peers: " + new NiceObjectMapper(new ObjectMapper()).writeValueAsString(trustedPeers));

        return new HttpArkClient(arkNetwork, restTemplate, trustedPeers);
    }

}
