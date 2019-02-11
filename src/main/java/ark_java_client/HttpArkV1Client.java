package ark_java_client;

import com.google.common.io.BaseEncoding;
import io.ark.core.Crypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.Base58;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class HttpArkV1Client implements ArkClient {

    private final ArkNetwork arkNetwork;
    private final RestTemplate restTemplate;
    private final List<Peer> trustedPeers;

    @Override
    public List<Transaction> getTransactions(Integer limit, Integer offset) {
        return restTemplate
                .exchange(
                        getRandomTrustedPeerUrl() + "/api/transactions?orderBy=timestamp:desc" +
                                "&limit={limit}" +
                                "&offset={offset}",
                        HttpMethod.GET,
                        null,
                        TransactionsResponse.class,
                        limit,
                        offset
                )
                .getBody()
                .getTransactions();
    }

    @Override
    public List<Transaction> getTransactionByRecipientAddress(String recipientAddress, Integer limit, Integer offset) {
        return restTemplate
                .exchange(
                        getRandomTrustedPeerUrl() + "/api/transactions?orderBy=timestamp:desc" +
                                "&limit={limit}" +
                                "&offset={offset}" +
                                "&recipientId={recipientId}",
                        HttpMethod.GET,
                        null,
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
        return restTemplate
                .exchange(
                        getRandomTrustedPeerUrl() + "/api/transactions/get?id={id}",
                        HttpMethod.GET,
                        null,
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
        List<Peer> targetPeers = new ArrayList<>(trustedPeers);
        Collections.shuffle(targetPeers);
        List<Peer> broadcastPeers = new ArrayList<>();
        if (targetPeers.size() <= nodes) {
            broadcastPeers.addAll(targetPeers);
        } else {
            for (int i = 0; i < nodes; i++) {
                broadcastPeers.add(targetPeers.get(i));
            }
        }
        log.info("Broadcasting transaction to " + broadcastPeers.size() + " peers: "
                + StringUtils.join(broadcastPeers.stream().map(Peer::getIp).collect(Collectors.toList()), ", "));

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
                            log.info("Failed to broadcast transaction to node " + peer.getIp() + ":" + peer.getApiPort()
                                    + ": rejected transaction");
                        }

                    } catch (RestClientResponseException re) {
                        log.info("Failed to broadcast transaction to node " + peer.getIp() + ":" + peer.getApiPort()
                                + ": " + re.getMessage(), re);
                        log.info("Response: " + re.getMessage());
                    } catch (Exception e) {
                        log.info("Failed to broadcast transaction to node " + peer.getIp() + ":" + peer.getApiPort()
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
        return restTemplate
                .exchange(
                        getRandomTrustedPeerUrl() + "/api/accounts/getBalance?address={id}",
                        HttpMethod.GET,
                        null,
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
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("nethash", arkNetwork.getNetHash());
        headers.set("version", arkNetwork.getVersion());
        headers.set("port", peer.getApiPort().toString());
        return headers;
    }

    private Peer getRandomTrustedPeer() {
        return trustedPeers.get(RandomUtils.nextInt(0, trustedPeers.size()));
    }

    private String getRandomTrustedPeerUrl() {
        return getPeerUrl(getRandomTrustedPeer());
    }

    private String getPeerUrl(Peer peer) {
        return arkNetwork.getHttpScheme() + "://" + peer.getIp() + ":" + peer.getApiPort();
    }

}
