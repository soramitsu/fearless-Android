package jp.co.soramitsu.shared_utils.encrypt;

/**
 * Test-only binding to the {@code jp.co.soramitsu.shared_utils} JNI exports of
 * libsr25519java, which downstream apps load under that package name. It
 * mirrors {@link jp.co.soramitsu.fearless_utils.encrypt.Sr25519} exactly.
 */
public final class Sr25519 {

    static {
        System.loadLibrary("sr25519java");
    }

    private Sr25519() { }

    public static native String test(String helloWhat);

    public static native boolean verify(byte[] signature, byte[] message, byte[] publicKey);

    public static native byte[] sign(byte[] publicKey, byte[] secret, byte[] message);

    public static native byte[] deriveKeypairHard(byte[] pair, byte[] cc);

    public static native byte[] deriveKeypairSoft(byte[] pair, byte[] cc);

    public static native byte[] derivePublicSoft(byte[] publicKey, byte[] cc);

    public static native byte[] keypairFromSeed(byte[] seed);

    public static native byte[] toEd25519Bytes(byte[] secret);

    public static native byte[] fromEd25519Bytes(byte[] expandedEdSecret);
}
