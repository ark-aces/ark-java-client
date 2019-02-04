package ark_java_client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.arkecosystem.crypto.identities.Address;
import org.arkecosystem.crypto.transactions.builder.Transfer;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class HttpArkClient implements ArkClient {

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
                new HttpEntity<>(getApiHttpHeaders()),
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
                new HttpEntity<>(getApiHttpHeaders()),
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
                new HttpEntity<>(getApiHttpHeaders()),
                new ParameterizedTypeReference<TransactionWrapper>() {},
                id
            ).getBody().getTransaction();
    }

    // todo: support second passphrase signing
    // todo: support different transaction types
    @Override
    public String broadcastTransaction(String recipientId, Long satoshiAmount, String vendorField, String passphrase, Integer nodes) {
        Transfer transferBuilder = new Transfer();
        transferBuilder.transaction.network = arkNetwork.getPubKeyHash();
        org.arkecosystem.crypto.transactions.Transaction transaction = transferBuilder
                .recipient(recipientId)
                .amount(satoshiAmount)
                .vendorField(vendorField)
                .sign(passphrase)
                .transaction;

        JsonNode transactionJsonNode;
        try {
            transactionJsonNode = new ObjectMapper().readValue(transaction.toJson(), JsonNode.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse write transaction as json string", e);
        }

        CreateArkV2TransactionsRequest requestBody = new CreateArkV2TransactionsRequest();
        requestBody.setTransactions(Arrays.asList(transactionJsonNode));

        HttpEntity<CreateArkV2TransactionsRequest> requestEntity = new HttpEntity<>(requestBody, getV2P2pApiHttpHeaders());

        // Broadcast transactions across n trusted peers or all peers if n > trusted peer count
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
        broadcastPeers.parallelStream()
            .forEach(peer -> {
                try {
                    ResponseEntity<ArkV2CreateTransactionsResponse> result = restTemplate
                            .exchange(
                                    getPeerUrl(peer) + "/api/transactions",
                                    HttpMethod.POST,
                                    requestEntity,
                                    ArkV2CreateTransactionsResponse.class
                            );

                    if (result.getBody().getData() != null && result.getBody().getData().getAccept() != null &&
                            result.getBody().getData().getAccept().size() > 0) {
                        transactionIds.addAll(result.getBody().getData().getAccept());
                    } else {
                        log.info("Failed to broadcast transaction to node " + peer.getIp() + ":" + peer.getP2pPort()
                                + ": rejected transaction");
                    }

                } catch (RestClientResponseException re) {
                    log.warn("Failed to broadcast transaction to node " + peer.getIp() + ":" + peer.getP2pPort()
                            + ": " + re.getMessage(), re);
                    log.info("Response: " + re.getMessage());
                } catch (Exception e) {
                    log.warn("Failed to broadcast transaction to node " + peer.getIp() + ":" + peer.getP2pPort()
                        + ": " + e.getMessage(), e);
                }
            });

        if (transactionIds.size() > 0) {
            // todo: return most common transaction id returned from nodes
            return transactionIds.get(0);
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
                new HttpEntity<>(getApiHttpHeaders()),
                new ParameterizedTypeReference<AccountBalance>() {},
                address
            )
            .getBody();
    }

    @Override
    public String getAddress(String passphrase) {
        return Address.fromPassphrase(passphrase, arkNetwork.getPubKeyHash());
    }

    private HttpHeaders getV2P2pApiHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("API-Version", "2");
        return headers;
    }

    private HttpHeaders getApiHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("API-Version", "1");
        return headers;
    }

    private Peer getRandomTrustedPeer() {
        return trustedPeers.get(RandomUtils.nextInt(0, trustedPeers.size()));
    }

    private String getRandomTrustedPeerUrl() {
        Peer peer = getRandomTrustedPeer();
        return getPeerUrl(peer);
    }

    private String getPeerUrl(Peer peer) {
        return arkNetwork.getHttpScheme() + "://" + peer.getIp() + ":" + peer.getApiPort();
    }

}
