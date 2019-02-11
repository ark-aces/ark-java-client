package ark_java_client;

import lombok.Data;

import java.util.List;

@Data
public class ArkNetworkSettings {
    private String networkVersion = "1";
    private String scheme;
    private List<ArkNetworkPeerSettings> trustedPeers;
    private String netHash;
    private Integer pubKeyHash = 23;
    private String epoch = "2017-03-21 13:00:00";
    private String version;
}
