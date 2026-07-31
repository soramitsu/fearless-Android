package jp.co.soramitsu.iroha.bridge;

/** Fail-closed error raised while validating, encoding, or signing an Iroha transfer. */
public final class IrohaBridgeException extends Exception {

    public IrohaBridgeException(String message) {
        super(message);
    }

    public IrohaBridgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
