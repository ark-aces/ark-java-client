package ark_java_client;

import ark_java_client.lib.NiceObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.BaseEncoding;
import io.ark.core.Crypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.RandomUtils;
import org.bitcoinj.core.Base58;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class HttpArkClient implements ArkClient {

    private final ArkNetwork arkNetwork;
    private final RestTemplate restTemplate;

    private List<Peer> peers = Collections.synchronizedList(new ArrayList<>());
    private List<Peer> trustedPeers = Collections.synchronizedList(new ArrayList<>());

    public void updatePeers() {
        Set<Peer> allPeers = Collections.synchronizedSet(new HashSet<>());
        arkNetwork.getPeerSettings().parallelStream().forEach(host -> {
            try {
                String baseUrl = arkNetwork.getHttpScheme() + "://" + host.getHostname() + ":" + host.getPort();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("API-Version", arkNetwork.getVersion());
                HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

                PeerList peerList = restTemplate
                    .exchange(
                        baseUrl + "/api/peers",
                        HttpMethod.GET,
                        requestEntity,
                        PeerList.class
                    )
                    .getBody();
                Set<Peer> peers = peerList.getPeers().stream()
                    .filter(peer -> Objects.equals(peer.getStatus(), "OK"))
                    // filter out private ip ranges that might show up in peer list
                    .filter(peer -> {
                        try {
                            InetAddress inetAddress = Inet4Address.getByName(peer.getIp());
                            return ! inetAddress.isSiteLocalAddress() && ! inetAddress.isLoopbackAddress();
                        } catch (UnknownHostException e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toSet());

                // Get peer api port from config
                List<Peer> configuredPeers = new ArrayList<>();
                for (Peer peer : peers) {
                    try {
                        String configUrl = arkNetwork.getHttpScheme() + "://" + peer.getIp() + ":" + peer.getPort() + "/config";
                        JsonNode responseBody = restTemplate.exchange(configUrl, HttpMethod.GET, null, JsonNode.class)
                                .getBody();
                        Integer configuredPort = responseBody.get("data")
                                .get("plugins")
                                .get("@arkecosystem/core-api")
                                .get("port")
                                .asInt();

                        Peer configuredPeer = new Peer();
                        configuredPeer.setDelay(peer.getDelay());
                        configuredPeer.setHeight(peer.getHeight());
                        configuredPeer.setIp(peer.getIp());
                        configuredPeer.setVersion(peer.getVersion());
                        configuredPeer.setPort(configuredPort);
                        configuredPeers.add(configuredPeer);
                    }
                    catch (Exception e) {
                        // Skip failed peers
                        log.warn("Discarding peer " + peer.getIp() + " due to request failure: " + e.getMessage());
                    }
                }

                allPeers.addAll(configuredPeers);
            } catch (Exception e) {
                // ignore failed hosts
            }
        });

        if (allPeers.size() < 1) {
            log.error("No peers available to connect to ark network");
            throw new RuntimeException("No Peers");
        }

        Set<Peer> newPeers = new HashSet<>(peers);
        newPeers.retainAll(allPeers);
        newPeers.addAll(allPeers);
        peers.addAll(newPeers);
        peers.retainAll(newPeers);

        log.info("Updated peers: ");
        log.info(new NiceObjectMapper(new ObjectMapper()).writeValueAsString(peers));

        // Update trusted peers
        HashSet<Peer> newTrustedPeers = new HashSet<>();
        arkNetwork.getTrustedPeerSettings().stream()
            .forEach(trustedPeerSetting -> {
                // Find in peers and add to trustedPeers
                allPeers.stream()
                    .filter(p -> p.getIp().equals(trustedPeerSetting.getHostname()) && p.getPort().equals(trustedPeerSetting.getPort()))
                    .findFirst()
                    .map(newTrustedPeers::add);
            });
        trustedPeers.addAll(newTrustedPeers);
        trustedPeers.retainAll(newTrustedPeers);

        if (trustedPeers.size() < 1) {
            log.error("No trusted peers available to connect to ark network");
            throw new RuntimeException("No trusted peers");
        }

        log.info("Updated trusted peers: ");
        log.info(new NiceObjectMapper(new ObjectMapper()).writeValueAsString(trustedPeers));
    }

    @Override
    public List<Transaction> getTransactions(Integer limit, Integer offset) {
        Peer peer = getRandomTrustedPeer();
        return restTemplate
            .exchange(
                getPeerUrl(peer) + "/api/transactions?orderBy=timestamp:desc" +
                    "&limit={limit}" +
                    "&offset={offset}",
                HttpMethod.GET,
                new HttpEntity<>(getHttpHeaders(peer)),
                TransactionsResponse.class,
                limit,
                offset
            )
            .getBody()
            .getTransactions();
    }

    @Override
    public List<Transaction> getTransactionByRecipientAddress(String recipientAddress, Integer limit, Integer offset) {
        Peer peer = getRandomTrustedPeer();
        return restTemplate
            .exchange(
                getPeerUrl(peer) + "/api/transactions?orderBy=timestamp:desc" +
                    "&limit={limit}" +
                    "&offset={offset}" +
                    "&recipientId={recipientId}",
                HttpMethod.GET,
                new HttpEntity<>(getHttpHeaders(peer)),
                TransactionsResponse.class,
                limit,
                offset,
                recipientAddress
            )
            .getBody()
            .getTransactions();
    }

    @Override
    public Transaction getTransaction(String id) {
        Peer peer = getRandomTrustedPeer();
        return restTemplate
            .exchange(
                getRandomTrustedPeerUrl() + "/api/transactions/get?id={id}",
                HttpMethod.GET,
                new HttpEntity<>(getHttpHeaders(peer)),
                new ParameterizedTypeReference<TransactionWrapper>() {},
                id
            ).getBody().getTransaction();
    }

    // todo: support second passphrase signing
    // todo: support different transaction types
    @Override
    public String broadcastTransaction(String recipientId, Long satoshiAmount, String vendorField, String passphrase, Integer nodes) {
        Date beginEpoch;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            beginEpoch = dateFormat.parse(arkNetwork.getEpoch());
        } catch (ParseException e) {
            throw new RuntimeException("Failed to parse epoch start date");
        }
        long timestamp = (new Date().getTime() - beginEpoch.getTime()) / 1000L;

        CreateArkTransactionRequest createArkTransactionRequest = new CreateArkTransactionRequest();
        createArkTransactionRequest.setType((byte) 0);
        createArkTransactionRequest.setRecipientId(recipientId);
        createArkTransactionRequest.setFee(10000000L);
        createArkTransactionRequest.setVendorField(vendorField);
        createArkTransactionRequest.setTimestamp(timestamp);
        createArkTransactionRequest.setAmount(satoshiAmount);

        // sign transaction
        String senderPublicKey = BaseEncoding.base16().lowerCase().encode(Crypto.getKeys(passphrase).getPubKey());
        createArkTransactionRequest.setSenderPublicKey(senderPublicKey);

        byte[] transactionBytes = getBytes(createArkTransactionRequest, senderPublicKey);
        ECKey.ECDSASignature signature = Crypto.signBytes(transactionBytes, passphrase);
        String signatureEncoded = BaseEncoding.base16().lowerCase().encode(signature.encodeToDER());

        createArkTransactionRequest.setSignature(signatureEncoded);

        String id = BaseEncoding.base16().lowerCase().encode(Sha256Hash.hash(transactionBytes));
        createArkTransactionRequest.setId(id);

        CreateArkTransactionsRequest createArkTransactionsRequest = new CreateArkTransactionsRequest();
        createArkTransactionsRequest.setTransactions(Arrays.asList(createArkTransactionRequest));

        // Broadcast transactions across all known peers in parallel
        List<Peer> targetPeers = new ArrayList<>();
        for (int i = 0; i < nodes; i++) {
            targetPeers.add(getRandomPeer());
        }
        List<String> transactionIds = Collections.synchronizedList(new ArrayList<>());
        targetPeers
            .parallelStream()
            .forEach(peer -> {
                try {
                    HttpHeaders headers = getHttpHeaders(peer);
                    HttpEntity<CreateArkTransactionsRequest> requestEntity = new HttpEntity<>(createArkTransactionsRequest, headers);

                    ResponseEntity<TransactionIdsWrapper> result = restTemplate
                            .exchange(
                                    getPeerUrl(peer) + "/peer/transactions",
                                    HttpMethod.POST,
                                    requestEntity,
                                    new ParameterizedTypeReference<TransactionIdsWrapper>() {
                                    }
                            );

                    if (result.getBody().getTransactionIds() != null && result.getBody().getTransactionIds().size() > 0) {
                        transactionIds.addAll(result.getBody().getTransactionIds());
                    } else {
                        log.info("Failed to broadcast transaction to node " + peer.getIp() + ":" + peer.getPort()
                                + ": rejected transaction");
                    }

                } catch (RestClientResponseException re) {
                    log.info("Failed to broadcast transaction to node " + peer.getIp() + ":" + peer.getPort()
                            + ": " + re.getMessage(), re);
                    log.info("Response: " + re.getMessage());
                } catch (Exception e) {
                    log.info("Failed to broadcast transaction to node " + peer.getIp() + ":" + peer.getPort()
                        + ": " + e.getMessage(), e);
                }
            });

        if (transactionIds.size() > 0) {
            // todo: return most common transaction id returned from nodes
            String bestTransactionId = transactionIds.get(0);
            return bestTransactionId;
        } else {
            throw new RuntimeException("Broadcast failed because no nodes accepted transaction");
        }
    }

    @Override
    public AccountBalance getBalance(String address) {
        Peer peer = getRandomTrustedPeer();
        return restTemplate
            .exchange(
                getPeerUrl(peer) + "/api/accounts/getBalance?address={id}",
                HttpMethod.GET,
                new HttpEntity<>(getHttpHeaders(peer)),
                new ParameterizedTypeReference<AccountBalance>() {},
                address
            )
            .getBody();
    }

    @Override
    public String getAddress(String passphrase) {
        Crypto.setNetworkVersion(arkNetwork.getPubKeyHash());
        return Crypto.getAddress(Crypto.getKeys(passphrase));
    }

    private byte[] getBytes(CreateArkTransactionRequest createArkTransactionRequest, String senderPublicKey) {
        ByteBuffer buffer = ByteBuffer.allocate(1000);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put(createArkTransactionRequest.getType());
        buffer.putInt((int) createArkTransactionRequest.getTimestamp()); // todo: fix downcast
        buffer.put(BaseEncoding.base16().lowerCase().decode(senderPublicKey));

        if(createArkTransactionRequest.getRecipientId() != null){
            buffer.put(Base58.decodeChecked(createArkTransactionRequest.getRecipientId()));
        } else {
            buffer.put(new byte[21]);
        }

        if (createArkTransactionRequest.getVendorField() != null) {
            byte[] vbytes = createArkTransactionRequest.getVendorField().getBytes();
            if(vbytes.length < 65){
                buffer.put(vbytes);
                buffer.put(new byte[64-vbytes.length]);
            }
        } else {
            buffer.put(new byte[64]);
        }

        buffer.putLong(createArkTransactionRequest.getAmount());
        buffer.putLong(createArkTransactionRequest.getFee());

        byte[] outBuffer = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(outBuffer);

        return outBuffer;
    }

    private HttpHeaders getHttpHeaders(Peer peer) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("API-Version", "1");
        return headers;
    }

    private Peer getRandomPeer() {
        return peers.get(RandomUtils.nextInt(peers.size()));
    }

    private Peer getRandomTrustedPeer() {
        return trustedPeers.get(RandomUtils.nextInt(trustedPeers.size()));
    }

    private String getRandomTrustedPeerUrl() {
        return getPeerUrl(getRandomTrustedPeer());
    }

    private String getPeerUrl(Peer peer) {
        return arkNetwork.getHttpScheme() + "://" + peer.getIp() + ":" + peer.getPort();
    }

}
