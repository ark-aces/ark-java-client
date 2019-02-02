package ark_java_client;

import lombok.Data;

@Data
public class Peer {
    private String ip;
    private Integer apiPort;
    private Integer p2pPort;
}
