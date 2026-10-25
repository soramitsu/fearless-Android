package jp.co.soramitsu.fearless_utils.encrypt;

public final class Sr25519 {

    /// Size of input SEED for derivation, bytes
    public static final int SEED_SIZE = 32;

    /// Size of CHAINCODE, bytes
    public static final int CHAINCODE_SIZE = 32;

    /// Size of SR25519 PUBLIC KEY, bytes
    public static final int PUBLIC_SIZE = 32;

    /// Size of SR25519 PRIVATE (SECRET) KEY, which consists of [32 bytes key | 32 bytes nonce]
    public static final int SECRET_SIZE = 64;

    /// Size of SR25519 SIGNATURE, bytes
    public static final int SIGNATURE_SIZE = 64;

    /// Size of SR25519 KEYPAIR. [32 bytes key | 32 bytes nonce | 32 bytes public]
    public static final int KEYPAIR_SIZE = 96;

    static {
        System.loadLibrary("sr25519java");
    }

    /** Completes native library initialization before a final signing authorization. */
    public static void ensureInitialized() { }

    public static native String test(String hello_what);

    /**
     * Verify a message and its corresponding against a public key;
     *
     * @param signature:  verify this signature
     * @param message:    arbitrary message
     * @param public_key: verify with this public key
     * @return true if signature is valid, false otherwise
     */
    public static native boolean verify(byte[] signature, byte[] message, byte[] public_key);

    /**
     * Sign a message
     * The combination of both public and private key must be provided.
     * This is effectively equivalent to a keypair.
     *
     * @param public_key: public key
     * @param secret:     private key (secret)
     * @param message:    Arbitrary message
     * @return the signature
     */
    public static native byte[] sign(byte[] public_key, byte[] secret, byte[] message);

    /**
     * Perform a derivation on a secret
     *
     * @param pair: existing keypair - input buffer of   KEYPAIR_SIZE bytes
     * @param cc:   chaincode - input buffer of   CHAINCODE_SIZE bytes
     * @return pre-allocated output buffer of   KEYPAIR_SIZE bytes
     */
    public static native byte[] deriveKeypairHard(byte[] pair, byte[] cc);


    /**
     * Perform a derivation on a secret
     *
     * @param pair: existing keypair - input buffer of   KEYPAIR_SIZE bytes
     * @param cc:   chaincode - input buffer of   CHAINCODE_SIZE bytes
     * @return keypair: pre-allocated output buffer of   KEYPAIR_SIZE bytes
     */
    public static native byte[] deriveKeypairSoft(byte[] pair, byte[] cc);

    /**
     * Perform a derivation on a publicKey
     *
     * @param public_key: public key - input buffer of   PUBLIC_SIZE bytes
     * @param cc:         chaincode - input buffer of   CHAINCODE_SIZE bytes
     * @return pre-allocated output buffer of   PUBLIC_SIZE bytes
     */
    public static native byte[] derivePublicSoft(byte[] public_key, byte[] cc);


    /**
     * Generate a key pair.
     *
     * @param seed: generation seed - input buffer of   SEED_SIZE bytes
     * @return keypair [32b key | 32b nonce | 32b public], pre-allocated output buffer of   KEYPAIR_SIZE bytes
     */
    public static native byte[] keypairFromSeed(byte[] seed);

    /**
     * Converts a secret key, provided as an array of 64 bytes,
     * to a corresponding ed25519 expanded secret key
     * @return an array of 64 bytes, with the first 32 bytes being the secret scalar shifted ed25519 style,
     * and the last 32 bytes being the seed for nonces
     */
    public static native byte[] toEd25519Bytes(byte[] secret);

    /**
     * Converts an ed25519 expanded secret key to a corresponding sr25519 secret key.
     * @return an array of 64 bytes, with the first 32 bytes being the secret scalar
     * represented canonically, and the last 32 bytes being the seed for nonces
     */
    public static native byte[] fromEd25519Bytes(byte[] ed_expanded_secret);
}
