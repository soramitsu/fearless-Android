package jp.co.soramitsu.iroha.bridge;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable, SDK-neutral inputs for one narrow fungible-asset transfer. */
public final class IrohaTransferRequest {

    private final String network;
    private final String chainId;
    private final String authority;
    private final String sourceAccountId;
    private final String assetDefinitionId;
    private final String sourceAssetId;
    private final String destinationAccountId;
    private final String amount;
    private final byte[] signingPublicKey;
    private final Map<String, String> transactionMetadata;

    public IrohaTransferRequest(
            String network,
            String chainId,
            String authority,
            String sourceAccountId,
            String assetDefinitionId,
            String sourceAssetId,
            String destinationAccountId,
            String amount,
            byte[] signingPublicKey
    ) {
        this(
                network,
                chainId,
                authority,
                sourceAccountId,
                assetDefinitionId,
                sourceAssetId,
                destinationAccountId,
                amount,
                signingPublicKey,
                Collections.emptyMap()
        );
    }

    public IrohaTransferRequest(
            String network,
            String chainId,
            String authority,
            String sourceAccountId,
            String assetDefinitionId,
            String sourceAssetId,
            String destinationAccountId,
            String amount,
            byte[] signingPublicKey,
            Map<String, String> transactionMetadata
    ) {
        this.network = Objects.requireNonNull(network, "network");
        this.chainId = Objects.requireNonNull(chainId, "chainId");
        this.authority = Objects.requireNonNull(authority, "authority");
        this.sourceAccountId = Objects.requireNonNull(sourceAccountId, "sourceAccountId");
        this.assetDefinitionId = Objects.requireNonNull(assetDefinitionId, "assetDefinitionId");
        this.sourceAssetId = Objects.requireNonNull(sourceAssetId, "sourceAssetId");
        this.destinationAccountId = Objects.requireNonNull(destinationAccountId, "destinationAccountId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.signingPublicKey = Arrays.copyOf(
                Objects.requireNonNull(signingPublicKey, "signingPublicKey"),
                signingPublicKey.length
        );
        this.transactionMetadata = immutableStringMap(transactionMetadata);
    }

    public String getNetwork() {
        return network;
    }

    public String getChainId() {
        return chainId;
    }

    public String getAuthority() {
        return authority;
    }

    public String getSourceAccountId() {
        return sourceAccountId;
    }

    public String getAssetDefinitionId() {
        return assetDefinitionId;
    }

    public String getSourceAssetId() {
        return sourceAssetId;
    }

    public String getDestinationAccountId() {
        return destinationAccountId;
    }

    public String getAmount() {
        return amount;
    }

    public byte[] getSigningPublicKey() {
        return Arrays.copyOf(signingPublicKey, signingPublicKey.length);
    }

    public Map<String, String> getTransactionMetadata() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(transactionMetadata));
    }

    private static Map<String, String> immutableStringMap(Map<String, String> supplied) {
        Objects.requireNonNull(supplied, "transactionMetadata");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : supplied.entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
                throw new IllegalArgumentException(
                        "transaction metadata keys and values must all be strings"
                );
            }
            copy.put((String) entry.getKey(), (String) entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
