package ark_java_client;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
@Data
public class ArkNetwork {

    private final String httpScheme;
    private final List<ArkNetworkPeerSettings> peerSettings;
    private final List<ArkNetworkPeerSettings> trustedPeerSettings;
    private final String netHash;
    private final String version;

}
