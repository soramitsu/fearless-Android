package jp.co.soramitsu.iroha.bridge;

import java.util.Arrays;
import java.util.Objects;

/** Versioned Norito transaction bytes and their canonical entrypoint hash. */
public final class IrohaSignedTransfer {

    private final byte[] signedTransaction;
    private final String transactionHashHex;

    IrohaSignedTransfer(byte[] signedTransaction, String transactionHashHex) {
        this.signedTransaction = Arrays.copyOf(
                Objects.requireNonNull(signedTransaction, "signedTransaction"),
                signedTransaction.length
        );
        this.transactionHashHex = Objects.requireNonNull(transactionHashHex, "transactionHashHex");
    }

    public byte[] getSignedTransaction() {
        return Arrays.copyOf(signedTransaction, signedTransaction.length);
    }

    public String getTransactionHashHex() {
        return transactionHashHex;
    }
}
