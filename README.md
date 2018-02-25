# Ark Java Client

A simple Java client for the Ark network. 

## Installation

Clone this repo locally and run maven install command to install locally:

```
git clone https://github.com/ark-aces/ark-java-client
mvn install
```

Add the following repository to your `pom.xml`:

```
<repository>
    <id>bintray-ark-aces-ark-java-client</id>
    <url>https://dl.bintray.com/ark-aces/ark-java-client</url>
</repository>
```

Import the client library into your project's `pom.xml` configuration `dependencies` 
section:


```
<dependency>
    <groupId>com.arkaces</groupId>
    <artifactId>ark-java-client</artifactId>
    <version>0.6.0</version>
</dependency>
```

## Usage

```java
ArkNetworkFactory arkNetworkFactory = new ArkNetworkFactory();
ArkNetwork arkNetwork = arkNetworkFactory.createFromYml("mainnet.yml");

HttpArkClientFactory httpArkClientFactory = new HttpArkClientFactory();
ArkClient arkClient = httpArkClientFactory.create(arkNetwork);

// Look up a transaction by transaction ID
String arkTransactionId = "83d3fa00ff3ac45ec859403ecedda48b870d73d9eeaddc34a6a8b79556141f43";
Transaction transaction = arkClient.getTransaction(arkTransactionId);

// Create a transaction
String address = "AewU1vEmPrtQNjdVo33cX84bfovY3jNAkV";
Long satoshiAmount = 10000L;
String vendorField = "test message";
String passphrase = "liar secret already much glow student crystal paddle ...";
String transactionId = arkClient
    .broadcastTransaction(address, satoshiAmount, vendorField, passphrase);
```

