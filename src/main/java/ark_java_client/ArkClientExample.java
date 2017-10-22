package ark_java_client;

/**
 * Runnable example usage.
 */
public class ArkClientExample {

    public static void main(String[] args) {
        ArkNetworkFactory arkNetworkFactory = new ArkNetworkFactory();
        ArkNetwork arkNetwork = arkNetworkFactory.createFromYml("mainnet.yml");

        HttpArkClientFactory httpArkClientFactory = new HttpArkClientFactory();
        ArkClient arkClient = httpArkClientFactory.create(arkNetwork);

        String arkTransactionId = "83d3fa00ff3ac45ec859403ecedda48b870d73d9eeaddc34a6a8b79556141f43";
        Transaction transaction = arkClient.getTransaction(arkTransactionId);
        System.out.println("Transaction Amount: " + transaction.getAmount());
    }

}
