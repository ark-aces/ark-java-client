package ark_java_client;

import lombok.Data;

import java.util.List;

@Data
public class ArkNetworkSettings {
    private String scheme;
    private List<ArkNetworkPeerSettings> seedPeers;
    private List<ArkNetworkPeerSettings> trustedPeers;
    private String netHash;
    private String version;
}
