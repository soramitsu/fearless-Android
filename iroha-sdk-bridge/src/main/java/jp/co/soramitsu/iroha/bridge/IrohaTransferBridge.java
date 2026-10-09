package jp.co.soramitsu.iroha.bridge;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.hyperledger.iroha.sdk.address.AccountAddress;
import org.hyperledger.iroha.sdk.address.AssetDefinitionIdEncoder;
import org.hyperledger.iroha.sdk.address.SingleKeyPayload;
import org.hyperledger.iroha.sdk.core.model.Executable;
import org.hyperledger.iroha.sdk.core.model.InstructionBox;
import org.hyperledger.iroha.sdk.core.model.TransactionPayload;
import org.hyperledger.iroha.sdk.core.model.instructions.TransferWirePayloadEncoder;
import org.hyperledger.iroha.sdk.crypto.IrohaHash;
import org.hyperledger.iroha.sdk.crypto.Signer;
import org.hyperledger.iroha.sdk.crypto.SigningException;
import org.hyperledger.iroha.sdk.tx.SignedTransaction;
import org.hyperledger.iroha.sdk.tx.TransactionBuilder;
import org.hyperledger.iroha.sdk.tx.norito.NoritoJavaCodecAdapter;
import org.hyperledger.iroha.sdk.tx.norito.SignedTransactionEncoder;

/**
 * Narrow Java-only adapter around the pinned upstream Iroha transaction codec.
 *
 * <p>This staged bridge supports Taira only. Nexus remains intentionally disabled. The supplied
 * private seed is consumed and zeroed on every return path; callers must not reuse it. Bouncy
 * Castle retains an internal private-key copy without a destruction API, so this cleanup is
 * best-effort and the bridge must remain disabled in production pending acceptance of that risk.
 */
public final class IrohaTransferBridge {

    public static final String SUPPORTED_NETWORK = "taira";
    public static final String SUPPORTED_CHAIN_ID = "iroha3-taira";
    public static final int SUPPORTED_CHAIN_DISCRIMINANT = 369;

    private static final int ED25519_KEY_BYTES = 32;
    private static final int ED25519_SIGNATURE_BYTES = 64;
    private static final int MAX_ACCOUNT_ID_CHARS = 512;
    private static final int MAX_ASSET_ID_CHARS = 1024;
    private static final int MAX_CHAIN_ID_CHARS = 128;
    private static final int MAX_AMOUNT_CHARS = 128;
    private static final int MAX_SIGNING_PAYLOAD_BYTES = 1024 * 1024;
    private static final int MAX_VERSIONED_TRANSACTION_BYTES = MAX_SIGNING_PAYLOAD_BYTES + 64 * 1024;
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final Pattern AMOUNT = Pattern.compile("(?:0|[1-9][0-9]*)(?:\\.[0-9]{0,27}[1-9])?");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final byte[] ED25519_SPKI_PREFIX = new byte[] {
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private final LongSupplier clock;

    public IrohaTransferBridge(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Builds and signs one transfer, consuming and clearing {@code privateKeySeed}. */
    public IrohaSignedTransfer buildAndSignTransfer(
            IrohaTransferRequest request,
            byte[] privateKeySeed
    ) throws IrohaBridgeException {
        byte[] workingSeed = null;
        byte[] expectedPublicKey = null;
        try {
            Objects.requireNonNull(request, "request");
            require(privateKeySeed != null, "private key seed is required");
            require(privateKeySeed.length == ED25519_KEY_BYTES, "private key seed must be 32 bytes");
            require(!isAllZero(privateKeySeed), "all-zero Ed25519 private key seed is forbidden");
            Map<String, String> requestMetadata = request.getTransactionMetadata();
            validateRequest(request, requestMetadata);

            workingSeed = Arrays.copyOf(privateKeySeed, privateKeySeed.length);
            Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(workingSeed, 0);
            Ed25519PublicKeyParameters publicKey = privateKey.generatePublicKey();
            expectedPublicKey = publicKey.getEncoded();
            byte[] requestedPublicKey = request.getSigningPublicKey();
            try {
                require(
                        MessageDigest.isEqual(expectedPublicKey, requestedPublicKey),
                        "signing public key does not match the private key seed"
                );
            } finally {
                Arrays.fill(requestedPublicKey, (byte) 0);
            }

            long creationTimeMillis = clock.getAsLong();
            require(creationTimeMillis >= 0, "creation time must be non-negative");

            InstructionBox transfer = TransferWirePayloadEncoder.encodeAssetTransfer(
                    request.getSourceAssetId(),
                    request.getAmount(),
                    request.getDestinationAccountId()
            );
            Executable executable = Executable.instructions(
                    Collections.singletonList(transfer)
            );
            TransactionPayload payload = new TransactionPayload(
                    request.getChainId(),
                    request.getAuthority(),
                    creationTimeMillis,
                    executable,
                    null,
                    null,
                    Collections.emptyMap()
            );

            Signer signer = new PrehashedEd25519Signer(privateKey, publicKey);
            SignedTransaction signed = new TransactionBuilder(new NoritoJavaCodecAdapter())
                    .encodeAndSign(payload, signer);
            byte[] versioned = SignedTransactionEncoder.encodeVersioned(signed);
            require(versioned.length > 1 && versioned[0] == 0x01, "invalid versioned transaction");

            // The pinned SDK's SignedTransactionHasher is known-defective: it frames the bare
            // transaction length as a fixed little-endian u64. The release-tag Rust entrypoint
            // uses minimal unsigned LEB128. Hash the exact bytes returned to the caller using
            // that reviewed framing until an upstream fixed SDK is published and re-reviewed.
            String hash = canonicalTransactionHashHex(versioned);
            require(HASH.matcher(hash).matches(), "invalid canonical transaction hash");
            return new IrohaSignedTransfer(versioned, hash);
        } catch (IrohaBridgeException error) {
            throw error;
        } catch (Exception error) {
            throw new IrohaBridgeException("Iroha transfer encoding or signing failed", error);
        } finally {
            if (expectedPublicKey != null) {
                Arrays.fill(expectedPublicKey, (byte) 0);
            }
            if (workingSeed != null) {
                Arrays.fill(workingSeed, (byte) 0);
            }
            if (privateKeySeed != null) {
                Arrays.fill(privateKeySeed, (byte) 0);
            }
        }
    }

    private static void validateRequest(
            IrohaTransferRequest request,
            Map<String, String> transactionMetadata
    ) throws IrohaBridgeException {
        require(SUPPORTED_NETWORK.equals(request.getNetwork()), "only Taira transfers are staged");
        require(SUPPORTED_CHAIN_ID.equals(request.getChainId()), "unexpected Taira chain id");
        requireText(request.getChainId(), "chain id", MAX_CHAIN_ID_CHARS);
        requireText(request.getAuthority(), "authority", MAX_ACCOUNT_ID_CHARS);
        requireText(request.getSourceAccountId(), "source account id", MAX_ACCOUNT_ID_CHARS);
        requireText(request.getDestinationAccountId(), "destination account id", MAX_ACCOUNT_ID_CHARS);
        requireText(request.getAssetDefinitionId(), "asset definition id", MAX_ASSET_ID_CHARS);
        requireText(request.getSourceAssetId(), "source asset id", MAX_ASSET_ID_CHARS);
        requireText(request.getAmount(), "amount", MAX_AMOUNT_CHARS);
        require(request.getAuthority().equals(request.getSourceAccountId()), "source account does not match authority");
        requireCanonicalAssetDefinition(request.getAssetDefinitionId());
        require(
                request.getSourceAssetId().equals(
                        request.getAssetDefinitionId() + "#" + request.getAuthority()
                ),
                "source asset id does not match asset definition and authority"
        );
        require(AMOUNT.matcher(request.getAmount()).matches(), "invalid transfer amount");
        require(new BigDecimal(request.getAmount()).signum() > 0, "transfer amount must be positive");
        validateTransactionMetadata(transactionMetadata);

        byte[] publicKey = request.getSigningPublicKey();
        byte[] authorityPublicKey = null;
        try {
            require(publicKey.length == ED25519_KEY_BYTES, "signing public key must be 32 bytes");
            SingleKeyPayload authorityKey = requireEd25519TairaAccount(
                    request.getAuthority(),
                    "authority"
            );
            authorityPublicKey = authorityKey.getPublicKey();
            require(
                    MessageDigest.isEqual(publicKey, authorityPublicKey),
                    "authority does not contain the signing public key"
            );
            requireEd25519TairaAccount(
                    request.getDestinationAccountId(),
                    "destination account id"
            );
        } finally {
            if (authorityPublicKey != null) {
                Arrays.fill(authorityPublicKey, (byte) 0);
            }
            Arrays.fill(publicKey, (byte) 0);
        }
    }

    private static void validateTransactionMetadata(Map<String, String> metadata)
            throws IrohaBridgeException {
        require(metadata != null, "transaction metadata is required");
        require(
                metadata.isEmpty(),
                "wallet-smoke transaction metadata is Nexus-only; staged Taira bridge requires empty metadata"
        );
    }

    private static AccountAddress requireTairaAccount(String value, String label)
            throws IrohaBridgeException {
        try {
            AccountAddress address = AccountAddress.fromI105(value, SUPPORTED_CHAIN_DISCRIMINANT);
            require(value.equals(address.toI105(SUPPORTED_CHAIN_DISCRIMINANT)), label + " is not canonical");
            return address;
        } catch (IrohaBridgeException error) {
            throw error;
        } catch (Exception error) {
            throw new IrohaBridgeException(label + " is not a canonical Taira I105 account", error);
        }
    }

    private static void requireCanonicalAssetDefinition(String value) throws IrohaBridgeException {
        byte[] definitionBytes = null;
        try {
            require(
                    AssetDefinitionIdEncoder.isCanonicalAddress(value),
                    "asset definition id must use canonical Base58 form"
            );
            definitionBytes = AssetDefinitionIdEncoder.parseAddressBytes(value);
            require(
                    value.equals(AssetDefinitionIdEncoder.encodeFromBytes(definitionBytes)),
                    "asset definition id does not round-trip canonically"
            );
        } catch (IrohaBridgeException error) {
            throw error;
        } catch (Exception error) {
            throw new IrohaBridgeException("invalid canonical asset definition id", error);
        } finally {
            if (definitionBytes != null) {
                Arrays.fill(definitionBytes, (byte) 0);
            }
        }
    }

    private static SingleKeyPayload requireEd25519TairaAccount(String value, String label)
            throws IrohaBridgeException {
        try {
            SingleKeyPayload key = requireTairaAccount(value, label).singleKeyPayload();
            require(key.curveId == 1, label + " must use Ed25519");
            return key;
        } catch (IrohaBridgeException error) {
            throw error;
        } catch (Exception error) {
            throw new IrohaBridgeException(label + " must be a single-key Ed25519 account", error);
        }
    }

    private static void requireText(String value, String label, int maximum)
            throws IrohaBridgeException {
        require(value != null && !value.isEmpty(), label + " is required");
        require(value.length() <= maximum, label + " is too long");
        require(value.equals(value.trim()), label + " must not contain surrounding whitespace");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            require(
                    !Character.isISOControl(character) && Character.getType(character) != Character.FORMAT,
                    label + " contains a control or formatting character"
            );
        }
    }

    private static void require(boolean condition, String message) throws IrohaBridgeException {
        if (!condition) {
            throw new IrohaBridgeException(message);
        }
    }

    private static boolean isAllZero(byte[] value) {
        int aggregate = 0;
        for (byte item : value) {
            aggregate |= item & 0xFF;
        }
        return aggregate == 0;
    }

    static String canonicalTransactionHashHex(byte[] versionedTransaction)
            throws IrohaBridgeException {
        require(versionedTransaction != null, "versioned transaction is required");
        require(
                versionedTransaction.length > 1 && versionedTransaction[0] == 0x01,
                "invalid versioned transaction"
        );
        require(
                versionedTransaction.length <= MAX_VERSIONED_TRANSACTION_BYTES,
                "versioned transaction is too large"
        );

        int bareLength = versionedTransaction.length - 1;
        byte[] compactLength = encodeCanonicalCompactLength(bareLength);
        byte[] canonical = null;
        byte[] hash = null;
        try {
            require(
                    decodeCanonicalCompactLength(compactLength) == bareLength,
                    "compact transaction length does not round-trip"
            );
            // Rust enum variant 0 is a little-endian u32, followed by the COMPACT_LEN length
            // and the bare SignedTransaction payload (the outer Java version byte is excluded).
            canonical = new byte[4 + compactLength.length + bareLength];
            System.arraycopy(compactLength, 0, canonical, 4, compactLength.length);
            System.arraycopy(
                    versionedTransaction,
                    1,
                    canonical,
                    4 + compactLength.length,
                    bareLength
            );
            hash = IrohaHash.prehash(canonical);
            char[] encoded = new char[hash.length * 2];
            for (int index = 0; index < hash.length; index++) {
                int value = hash[index] & 0xFF;
                encoded[index * 2] = HEX[value >>> 4];
                encoded[index * 2 + 1] = HEX[value & 0x0F];
            }
            return new String(encoded);
        } finally {
            Arrays.fill(compactLength, (byte) 0);
            if (canonical != null) {
                Arrays.fill(canonical, (byte) 0);
            }
            if (hash != null) {
                Arrays.fill(hash, (byte) 0);
            }
        }
    }

    static byte[] encodeCanonicalCompactLength(int value) throws IrohaBridgeException {
        require(value >= 0, "compact length must be non-negative");
        byte[] buffer = new byte[5];
        int remaining = value;
        int size = 0;
        do {
            int next = remaining & 0x7F;
            remaining >>>= 7;
            if (remaining != 0) {
                next |= 0x80;
            }
            buffer[size++] = (byte) next;
        } while (remaining != 0);
        return Arrays.copyOf(buffer, size);
    }

    static int decodeCanonicalCompactLength(byte[] encoded) throws IrohaBridgeException {
        require(encoded != null, "compact length is required");
        require(encoded.length > 0 && encoded.length <= 5, "invalid compact length size");

        long value = 0;
        boolean terminated = false;
        for (int index = 0; index < encoded.length; index++) {
            int current = encoded[index] & 0xFF;
            int payload = current & 0x7F;
            if (index == 4) {
                require(payload <= 0x07, "compact length exceeds signed integer range");
            }
            value |= (long) payload << (index * 7);
            if ((current & 0x80) == 0) {
                require(index == encoded.length - 1, "compact length has trailing bytes");
                terminated = true;
                break;
            }
        }
        require(terminated, "compact length is unterminated");
        require(value <= Integer.MAX_VALUE, "compact length exceeds signed integer range");

        byte[] minimal = encodeCanonicalCompactLength((int) value);
        try {
            require(Arrays.equals(encoded, minimal), "compact length is overlong");
        } finally {
            Arrays.fill(minimal, (byte) 0);
        }
        return (int) value;
    }

    private static final class PrehashedEd25519Signer implements Signer {

        private final Ed25519PrivateKeyParameters privateKey;
        private final Ed25519PublicKeyParameters publicKey;
        private final byte[] subjectPublicKeyInfo;

        private PrehashedEd25519Signer(
                Ed25519PrivateKeyParameters privateKey,
                Ed25519PublicKeyParameters publicKey
        ) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
            byte[] rawPublicKey = publicKey.getEncoded();
            this.subjectPublicKeyInfo = new byte[ED25519_SPKI_PREFIX.length + rawPublicKey.length];
            System.arraycopy(ED25519_SPKI_PREFIX, 0, subjectPublicKeyInfo, 0, ED25519_SPKI_PREFIX.length);
            System.arraycopy(rawPublicKey, 0, subjectPublicKeyInfo, ED25519_SPKI_PREFIX.length, rawPublicKey.length);
            Arrays.fill(rawPublicKey, (byte) 0);
        }

        @Override
        public byte[] sign(byte[] message) throws SigningException {
            if (message == null || message.length == 0 || message.length > MAX_SIGNING_PAYLOAD_BYTES) {
                throw new SigningException("invalid Iroha signing payload length");
            }

            byte[] prehash = IrohaHash.prehash(message);
            try {
                Ed25519Signer signer = new Ed25519Signer();
                signer.init(true, privateKey);
                signer.update(prehash, 0, prehash.length);
                byte[] signature = signer.generateSignature();
                if (signature.length != ED25519_SIGNATURE_BYTES) {
                    Arrays.fill(signature, (byte) 0);
                    throw new SigningException("unexpected Ed25519 signature length");
                }

                Ed25519Signer verifier = new Ed25519Signer();
                verifier.init(false, publicKey);
                verifier.update(prehash, 0, prehash.length);
                if (!verifier.verifySignature(signature)) {
                    Arrays.fill(signature, (byte) 0);
                    throw new SigningException("Ed25519 self-verification failed");
                }
                return signature;
            } finally {
                Arrays.fill(prehash, (byte) 0);
            }
        }

        @Override
        public byte[] publicKey() {
            return Arrays.copyOf(subjectPublicKeyInfo, subjectPublicKeyInfo.length);
        }

        @Override
        public String algorithm() {
            return "Ed25519";
        }
    }
}
