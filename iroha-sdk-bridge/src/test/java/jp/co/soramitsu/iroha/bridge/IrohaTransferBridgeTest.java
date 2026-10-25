package jp.co.soramitsu.iroha.bridge;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.hyperledger.iroha.sdk.core.model.Executable;
import org.hyperledger.iroha.sdk.core.model.InstructionBox;
import org.hyperledger.iroha.sdk.core.model.TransactionPayload;
import org.hyperledger.iroha.sdk.core.model.WirePayload;
import org.hyperledger.iroha.sdk.core.model.instructions.TransferWirePayloadEncoder;
import org.hyperledger.iroha.sdk.core.model.instructions.TransferWirePayloadEncoder.DecodedAssetTransfer;
import org.hyperledger.iroha.sdk.crypto.IrohaHash;
import org.hyperledger.iroha.sdk.tx.SignedTransaction;
import org.hyperledger.iroha.sdk.tx.SignedTransactionHasher;
import org.hyperledger.iroha.sdk.tx.norito.NoritoJavaCodecAdapter;
import org.hyperledger.iroha.sdk.tx.norito.SignedTransactionEncoder;
import org.junit.Test;

public final class IrohaTransferBridgeTest {

    private static final Properties HASH_VECTOR = loadHashVector();
    private static final long CREATION_TIME_MILLIS = 1_735_000_300_000L;
    private static final String AUTHORITY =
            "testuﾛ1NeｱviV1aDbDｾNﾙZMｸAﾍﾐoﾍWc1j5cﾙyｲiﾕﾚcxﾘVﾛ6QG656";
    private static final String DESTINATION =
            "testuﾛ1Npﾃﾕヱﾇq11pｳﾘ2ｱ5ﾇｦiCJKjRﾔzｷNMNﾆｹﾕPCｳﾙFvｵE9LBLB";
    private static final String OTHER_DESTINATION =
            "testuﾛ1NﾊﾊﾅuVﾛGｷrFYﾕ1cﾒｷﾖﾃﾇﾅEﾔﾚｷﾜjmoｴ7ｸﾒMWH2ﾏk4DEMRY";
    private static final String NEXUS_AUTHORITY =
            "sorauﾛ1Pcﾅ2ﾗtﾉaﾘLﾕｽ2MヱﾐﾎｳﾓヱｷﾆｲMﾒSﾏｱヱｷJヱFmJﾇMs6YN687Y";
    private static final String SECP256K1_ACCOUNT =
            "test2QHHｴﾒﾔBgfﾐdｹヱa6ﾊyqVﾐｻrpruﾖZﾗｾWkｳzqGGﾕdｳｳﾏｻiM2HYQ6";
    private static final String MULTISIG_ACCOUNT =
            "test6ﾋRNｽYﾖﾑｱCAfzVｳZﾈHBｵｦUｳRKeｿMFdﾄeﾇﾆﾂﾗZﾙｱKｹｹﾏﾘｹﾍgﾉﾊｱｽL82HCTR";
    private static final String ASSET_DEFINITION = "61CtjvNd9T3THAR65GsMVHr82Bjc";
    private static final String WIRE_AUTHORITY =
            "sorauﾛ1NeｱviV1aDbDｾNﾙZMｸAﾍﾐoﾍWc1j5cﾙyｲiﾕﾚcxﾘVﾛ6QG656";
    private static final String WIRE_DESTINATION =
            "sorauﾛ1Npﾃﾕヱﾇq11pｳﾘ2ｱ5ﾇｦiCJKjRﾔzｷNMNﾆｹﾕPCｳﾙFvｵE9LBLB";
    private static final byte[] PUBLIC_KEY = hex(
            "03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8"
    );
    private static final String GOLDEN_HASH = vector("canonical.hash");
    private static final String PINNED_SDK_DEFECTIVE_HASH =
            vector("pinned.sdk.defective.hash");
    private static final String GOLDEN_BASE64 = vector("versioned.base64");
    private static final String ROUTE_GOVERNANCE_ACTION_HASH =
            "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String WALLET_COMMIT =
            "0123456789abcdef0123456789abcdef01234567";

    @Test
    public void buildsDeterministicGoldenTransferWithCanonicalHashAndSignature() throws Exception {
        byte[] seed = seed();
        IrohaSignedTransfer result = bridge(CREATION_TIME_MILLIS)
                .buildAndSignTransfer(validRequest("1.25", DESTINATION), seed);

        assertAllZero(seed);
        assertEquals("1", vector("schema.version"));
        assertEquals("v2.0.0-rc.2.1-fearless-mobile-sdk.3", vector("source.tag"));
        assertEquals("4f8cfbdd17aa6a3b049e619f23ec02501e5297b6", vector("source.commit"));
        assertEquals("565", vector("versioned.bytes"));
        assertEquals("564", vector("bare.bytes"));
        assertEquals("b404", vector("compact.length.hex"));
        assertEquals("00000000b404", vector("canonical.prefix.hex"));
        assertEquals(GOLDEN_HASH, result.getTransactionHashHex());
        assertEquals(GOLDEN_BASE64, Base64.getEncoder().encodeToString(result.getSignedTransaction()));
        assertEquals(1, result.getSignedTransaction()[0]);

        SignedTransaction signed = SignedTransactionEncoder.decodeVersioned(result.getSignedTransaction());
        assertEquals(564, SignedTransactionEncoder.encode(signed).length);
        assertArrayEquals(
                new byte[] {(byte) 0xB4, 0x04},
                IrohaTransferBridge.encodeCanonicalCompactLength(564)
        );
        assertEquals(
                result.getTransactionHashHex(),
                IrohaTransferBridge.canonicalTransactionHashHex(result.getSignedTransaction())
        );
        // Regression guard for the pinned upstream defect: its fixed-u64 framing must never be
        // mistaken for the compact release-tag Rust transaction entrypoint.
        assertEquals(PINNED_SDK_DEFECTIVE_HASH, SignedTransactionHasher.hashHex(signed));
        assertNotEquals(
                result.getTransactionHashHex(),
                SignedTransactionHasher.hashHex(signed)
        );

        TransactionPayload payload = new NoritoJavaCodecAdapter().decodeTransaction(signed.encodedPayload());
        assertEquals(IrohaTransferBridge.SUPPORTED_CHAIN_ID, payload.getChainId());
        assertEquals(WIRE_AUTHORITY, payload.getAuthority());
        assertEquals(CREATION_TIME_MILLIS, payload.getCreationTimeMs());
        assertEquals(null, payload.getTimeToLiveMs());
        assertEquals(null, payload.getNonce());
        assertTrue(payload.getMetadata().isEmpty());
        assertTrue(payload.getExecutable() instanceof Executable.Instructions);
        List<InstructionBox> instructions =
                ((Executable.Instructions) payload.getExecutable()).getInstructions();
        assertEquals(1, instructions.size());
        InstructionBox instruction = instructions.get(0);
        assertEquals(TransferWirePayloadEncoder.WIRE_NAME, instruction.getName());
        assertTrue(instruction.getPayload() instanceof WirePayload);
        DecodedAssetTransfer decodedTransfer =
                TransferWirePayloadEncoder.decodeAssetTransferPayload$core_jvm(
                        ((WirePayload) instruction.getPayload()).getPayloadBytes()
                );
        assertEquals(ASSET_DEFINITION + "#" + WIRE_AUTHORITY, decodedTransfer.getAssetId());
        assertEquals("1.25", decodedTransfer.getAmount());
        assertEquals(WIRE_DESTINATION, decodedTransfer.getDestinationAccountId());

        byte[] prehash = IrohaHash.prehash(signed.encodedPayload());
        try {
            assertTrue(verify(PUBLIC_KEY, prehash, signed.signature()));
        } finally {
            Arrays.fill(prehash, (byte) 0);
        }
    }

    @Test
    public void rejectsCanonicalNexusWalletSmokeMetadataBeforeClockOrSigning() {
        AtomicBoolean clockCalled = new AtomicBoolean(false);
        IrohaTransferBridge tairaBridge = new IrohaTransferBridge(() -> {
            clockCalled.set(true);
            return CREATION_TIME_MILLIS;
        });
        byte[] seed = seed();

        IrohaBridgeException error = assertThrows(
                IrohaBridgeException.class,
                () -> tairaBridge.buildAndSignTransfer(
                        validRequestWithMetadata("1.25", DESTINATION, walletSmokeMetadata()),
                        seed
                )
        );

        assertTrue(error.getMessage().contains("Nexus-only"));
        assertFalse(clockCalled.get());
        assertAllZero(seed);
    }

    @Test
    public void rejectsMalformedWalletSmokeMetadataBeforeSigning() {
        for (String key : walletSmokeMetadata().keySet()) {
            Map<String, String> missing = walletSmokeMetadata();
            missing.remove(key);
            assertMetadataRejected(missing, "key set");
        }

        Map<String, String> extra = walletSmokeMetadata();
        extra.put("unexpected", "value");
        assertMetadataRejected(extra, "key set");

        Map<String, String> wrongCaseKey = walletSmokeMetadata();
        wrongCaseKey.put("Evidence_role", wrongCaseKey.remove("evidence_role"));
        assertMetadataRejected(wrongCaseKey, "key set");

        Map<String, String> wrongHashKeyCase = walletSmokeMetadata();
        wrongHashKeyCase.put(
                "route_Governance_action_hash",
                wrongHashKeyCase.remove("route_governance_action_hash")
        );
        assertMetadataRejected(wrongHashKeyCase, "key set");

        assertMetadataValueRejected("evidence_role", "wallet_smoke", "evidence_role");
        assertMetadataValueRejected("evidence_role", "Wallet-Smoke", "evidence_role");
        assertMetadataValueRejected("evidence_role", "wallet-smoke\n", "evidence_role");
        assertMetadataValueRejected("wallet_platform", "Android", "wallet_platform");
        assertMetadataValueRejected("wallet_platform", "ios", "wallet_platform");
        assertMetadataValueRejected("wallet_platform", "android\u0000", "wallet_platform");

        String[] invalidRouteHashes = {
            "", "sha256:", "SHA256:" + repeat('a', 64), repeat('a', 64),
            "sha256:" + repeat('a', 63), "sha256:" + repeat('a', 65),
            "sha256:" + repeat('A', 64), "sha256:" + repeat('g', 64),
            "sha256:" + repeat('0', 64), "sha256:" + repeat('a', 63) + "\n",
            " sha256:" + repeat('a', 64), "sha256:" + repeat('a', 64) + " ",
            "sha256:" + repeat('\uff11', 64)
        };
        for (String routeHash : invalidRouteHashes) {
            assertMetadataValueRejected(
                    "route_governance_action_hash",
                    routeHash,
                    "route_governance_action_hash"
            );
        }

        String[] invalidCommits = {
            "", repeat('a', 39), repeat('a', 41), repeat('A', 40), repeat('g', 40),
            repeat('0', 40), "sha256:" + repeat('a', 40), repeat('a', 39) + "\n",
            " " + repeat('a', 40), repeat('a', 40) + " ", repeat('\uff11', 40)
        };
        for (String commit : invalidCommits) {
            assertMetadataValueRejected("wallet_commit", commit, "wallet_commit");
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        Map<String, String> nonStringValue = (Map) walletSmokeMetadata();
        ((Map) nonStringValue).put("wallet_commit", 7);
        IllegalArgumentException nonStringError = assertThrows(
                IllegalArgumentException.class,
                () -> validRequestWithMetadata("1.25", DESTINATION, nonStringValue)
        );
        assertTrue(nonStringError.getMessage().contains("must all be strings"));

        @SuppressWarnings({"rawtypes", "unchecked"})
        Map<String, String> nullValue = (Map) walletSmokeMetadata();
        ((Map) nullValue).put("wallet_commit", null);
        assertThrows(
                IllegalArgumentException.class,
                () -> validRequestWithMetadata("1.25", DESTINATION, nullValue)
        );
    }

    @Test
    public void transactionMetadataIsDefensivelyCopiedAndImmutable() throws Exception {
        Map<String, String> supplied = walletSmokeMetadata();
        IrohaTransferRequest request = validRequestWithMetadata("1.25", DESTINATION, supplied);
        supplied.clear();
        supplied.put("attacker", "injected");

        assertEquals(walletSmokeMetadata(), request.getTransactionMetadata());
        Map<String, String> returned = request.getTransactionMetadata();
        assertThrows(
                UnsupportedOperationException.class,
                () -> returned.put("attacker", "injected")
        );
        assertNotSame(returned, request.getTransactionMetadata());

        assertMetadataRejected(request.getTransactionMetadata(), "Nexus-only");
    }

    @Test
    public void everyNonEmptyMetadataMapAbortsBeforeClockSigningOrSubmission() {
        AtomicBoolean clockCalled = new AtomicBoolean(false);
        IrohaTransferBridge failingBridge = new IrohaTransferBridge(
                () -> {
                    clockCalled.set(true);
                    return CREATION_TIME_MILLIS;
                }
        );
        byte[] seed = seed();

        IrohaBridgeException error = assertThrows(
                IrohaBridgeException.class,
                () -> failingBridge.buildAndSignTransfer(
                        validRequestWithMetadata("1.25", DESTINATION, walletSmokeMetadata()),
                        seed
                )
        );

        assertTrue(error.getMessage().contains("Nexus-only"));
        assertFalse(clockCalled.get());
        assertAllZero(seed);
    }

    @Test
    public void compactLengthEncodingIsMinimalAtEveryBoundaryAndRejectsOverlongInputs()
            throws Exception {
        Object[][] boundaries = {
            {0, new byte[] {0x00}},
            {1, new byte[] {0x01}},
            {127, new byte[] {0x7F}},
            {128, new byte[] {(byte) 0x80, 0x01}},
            {16_383, new byte[] {(byte) 0xFF, 0x7F}},
            {16_384, new byte[] {(byte) 0x80, (byte) 0x80, 0x01}},
            {2_097_151, new byte[] {(byte) 0xFF, (byte) 0xFF, 0x7F}},
            {2_097_152, new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01}},
            {268_435_455, new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F}},
            {268_435_456, new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01}},
            {Integer.MAX_VALUE,
                    new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x07}}
        };
        for (Object[] boundary : boundaries) {
            int value = (Integer) boundary[0];
            byte[] expected = (byte[]) boundary[1];
            byte[] encoded = IrohaTransferBridge.encodeCanonicalCompactLength(value);
            assertArrayEquals(expected, encoded);
            assertEquals(value, IrohaTransferBridge.decodeCanonicalCompactLength(encoded));
        }

        assertCompactRejected(new byte[] {(byte) 0x80, 0x00}, "overlong");
        assertCompactRejected(new byte[] {(byte) 0x81, 0x00}, "overlong");
        assertCompactRejected(
                new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00},
                "overlong"
        );
        assertCompactRejected(new byte[] {(byte) 0x80}, "unterminated");
        assertCompactRejected(new byte[] {0x00, 0x00}, "trailing");
        assertCompactRejected(
                new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x08},
                "signed integer range"
        );
        assertCompactRejected(new byte[0], "size");
        assertCompactRejected(new byte[6], "size");
        IrohaBridgeException nullError = assertThrows(
                IrohaBridgeException.class,
                () -> IrohaTransferBridge.decodeCanonicalCompactLength(null)
        );
        assertTrue(nullError.getMessage().contains("required"));
        IrohaBridgeException negativeError = assertThrows(
                IrohaBridgeException.class,
                () -> IrohaTransferBridge.encodeCanonicalCompactLength(-1)
        );
        assertTrue(negativeError.getMessage().contains("non-negative"));
    }

    @Test
    public void rejectsWrongNetworkChainAddressKeyAndSourceBindingsAndClearsSecrets() {
        assertRejected(
                request("nexus", IrohaTransferBridge.SUPPORTED_CHAIN_ID, AUTHORITY, AUTHORITY,
                        ASSET_DEFINITION, ASSET_DEFINITION + "#" + AUTHORITY, DESTINATION, "1.25", PUBLIC_KEY),
                "only Taira"
        );
        assertRejected(
                request(IrohaTransferBridge.SUPPORTED_NETWORK, "sora:nexus:global", AUTHORITY, AUTHORITY,
                        ASSET_DEFINITION, ASSET_DEFINITION + "#" + AUTHORITY, DESTINATION, "1.25", PUBLIC_KEY),
                "chain id"
        );
        assertRejected(
                request(IrohaTransferBridge.SUPPORTED_NETWORK, IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                        NEXUS_AUTHORITY, NEXUS_AUTHORITY, ASSET_DEFINITION,
                        ASSET_DEFINITION + "#" + NEXUS_AUTHORITY, DESTINATION, "1.25", PUBLIC_KEY),
                "Taira I105"
        );
        assertRejected(
                request(IrohaTransferBridge.SUPPORTED_NETWORK, IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                        SECP256K1_ACCOUNT, SECP256K1_ACCOUNT, ASSET_DEFINITION,
                        ASSET_DEFINITION + "#" + SECP256K1_ACCOUNT, DESTINATION, "1.25", PUBLIC_KEY),
                "Ed25519"
        );
        assertRejected(
                request(IrohaTransferBridge.SUPPORTED_NETWORK, IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                        AUTHORITY, AUTHORITY, ASSET_DEFINITION, ASSET_DEFINITION + "#" + AUTHORITY,
                        SECP256K1_ACCOUNT, "1.25", PUBLIC_KEY),
                "Ed25519"
        );
        assertRejected(
                request(IrohaTransferBridge.SUPPORTED_NETWORK, IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                        MULTISIG_ACCOUNT, MULTISIG_ACCOUNT, ASSET_DEFINITION,
                        ASSET_DEFINITION + "#" + MULTISIG_ACCOUNT, DESTINATION, "1.25", PUBLIC_KEY),
                "single-key"
        );
        assertRejected(
                request(IrohaTransferBridge.SUPPORTED_NETWORK, IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                        AUTHORITY, AUTHORITY, ASSET_DEFINITION, ASSET_DEFINITION + "#" + AUTHORITY,
                        MULTISIG_ACCOUNT, "1.25", PUBLIC_KEY),
                "single-key"
        );
        assertRejected(
                request(IrohaTransferBridge.SUPPORTED_NETWORK, IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                        AUTHORITY, DESTINATION, ASSET_DEFINITION, ASSET_DEFINITION + "#" + AUTHORITY,
                        DESTINATION, "1.25", PUBLIC_KEY),
                "source account"
        );
        assertRejected(
                request(IrohaTransferBridge.SUPPORTED_NETWORK, IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                        AUTHORITY, AUTHORITY, ASSET_DEFINITION, "1111111111111111111111111111#" + AUTHORITY,
                        DESTINATION, "1.25", PUBLIC_KEY),
                "source asset id"
        );

        byte[] wrongPublicKey = Arrays.copyOf(PUBLIC_KEY, PUBLIC_KEY.length);
        wrongPublicKey[0] ^= 1;
        assertRejected(
                request(IrohaTransferBridge.SUPPORTED_NETWORK, IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                        AUTHORITY, AUTHORITY, ASSET_DEFINITION, ASSET_DEFINITION + "#" + AUTHORITY,
                        DESTINATION, "1.25", wrongPublicKey),
                "authority does not contain"
        );

        byte[] wrongSeed = new byte[32];
        Arrays.fill(wrongSeed, (byte) 9);
        IrohaBridgeException mismatch = assertThrows(
                IrohaBridgeException.class,
                () -> bridge(CREATION_TIME_MILLIS).buildAndSignTransfer(validRequest("1.25", DESTINATION), wrongSeed)
        );
        assertTrue(mismatch.getMessage().contains("private key seed"));
        assertAllZero(wrongSeed);

        byte[] shortSeed = new byte[31];
        IrohaBridgeException shortSeedError = assertThrows(
                IrohaBridgeException.class,
                () -> bridge(CREATION_TIME_MILLIS).buildAndSignTransfer(
                        validRequest("1.25", DESTINATION),
                        shortSeed
                )
        );
        assertTrue(shortSeedError.getMessage().contains("32 bytes"));
        assertAllZero(shortSeed);

        byte[] zeroSeed = new byte[32];
        IrohaBridgeException zeroSeedError = assertThrows(
                IrohaBridgeException.class,
                () -> bridge(CREATION_TIME_MILLIS).buildAndSignTransfer(
                        validRequest("1.25", DESTINATION),
                        zeroSeed
                )
        );
        assertTrue(zeroSeedError.getMessage().contains("all-zero"));
        assertAllZero(zeroSeed);

        IrohaBridgeException nullSeedError = assertThrows(
                IrohaBridgeException.class,
                () -> bridge(CREATION_TIME_MILLIS).buildAndSignTransfer(
                        validRequest("1.25", DESTINATION),
                        null
                )
        );
        assertTrue(nullSeedError.getMessage().contains("seed is required"));

        for (int keyLength : new int[] {0, 31, 33, 1024}) {
            assertRejected(
                    request(IrohaTransferBridge.SUPPORTED_NETWORK, IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                            AUTHORITY, AUTHORITY, ASSET_DEFINITION, ASSET_DEFINITION + "#" + AUTHORITY,
                            DESTINATION, "1.25", new byte[keyLength]),
                    "public key must be 32 bytes"
            );
        }
    }

    @Test
    public void rejectsNonCanonicalMalformedZeroAndOversizedAmountsAndClearsSecrets() {
        String[] invalid = {
            "", "0", "0.0", "1.0", "1.20", "00.1", "01", "-1", "+1", "1e3", "1.",
            ".1", " 1", "1 ", "１", "0.00000000000000000000000000000",
            "1.00000000000000000000000000001",
            new String(new byte[129], StandardCharsets.ISO_8859_1).replace('\0', '1')
        };

        for (String amount : invalid) {
            assertRejected(validRequest(amount, DESTINATION), "amount");
        }

        byte[] acceptedSeed = seed();
        try {
            IrohaSignedTransfer result = bridge(CREATION_TIME_MILLIS).buildAndSignTransfer(
                    validRequest("0.0000000000000000000000000001", DESTINATION),
                    acceptedSeed
            );
            assertTrue(result.getSignedTransaction().length > 1);
        } catch (IrohaBridgeException error) {
            throw new AssertionError(error);
        }
        assertAllZero(acceptedSeed);
    }

    @Test
    public void clearsSeedWhenClockOrValidationFailsAndRejectsControlCharacters() {
        byte[] seed = seed();
        LongSupplier failingClock = () -> {
            throw new IllegalStateException("clock unavailable");
        };
        IrohaBridgeException clockError = assertThrows(
                IrohaBridgeException.class,
                () -> new IrohaTransferBridge(failingClock).buildAndSignTransfer(
                        validRequest("1.25", DESTINATION),
                        seed
                )
        );
        assertTrue(clockError.getMessage().contains("encoding or signing failed"));
        assertAllZero(seed);

        byte[] nullRequestSeed = seed();
        IrohaBridgeException nullRequestError = assertThrows(
                IrohaBridgeException.class,
                () -> bridge(CREATION_TIME_MILLIS).buildAndSignTransfer(null, nullRequestSeed)
        );
        assertTrue(nullRequestError.getMessage().contains("encoding or signing failed"));
        assertAllZero(nullRequestSeed);

        String controlledDestination = DESTINATION.substring(0, 8) + "\u0000" + DESTINATION.substring(8);
        assertRejected(validRequest("1.25", controlledDestination), "control");
        assertRejected(
                request(IrohaTransferBridge.SUPPORTED_NETWORK, IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                        AUTHORITY, AUTHORITY, "xor%sora", "xor%sora#" + AUTHORITY,
                        DESTINATION, "1.25", PUBLIC_KEY),
                "asset definition id"
        );
        assertRejected(
                request(IrohaTransferBridge.SUPPORTED_NETWORK, IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                        AUTHORITY, AUTHORITY, "xor#sora", "xor#sora#" + AUTHORITY,
                        DESTINATION, "1.25", PUBLIC_KEY),
                "asset definition id"
        );
        String oversizedAsset = repeat('a', 1_023) + "#b";
        assertRejected(
                request(IrohaTransferBridge.SUPPORTED_NETWORK, IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                        AUTHORITY, AUTHORITY, oversizedAsset, oversizedAsset + "#" + AUTHORITY,
                        DESTINATION, "1.25", PUBLIC_KEY),
                "asset definition id is too long"
        );
        String oversizedAccount = repeat('x', 513);
        assertRejected(
                request(IrohaTransferBridge.SUPPORTED_NETWORK, IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                        oversizedAccount, oversizedAccount, ASSET_DEFINITION,
                        ASSET_DEFINITION + "#" + oversizedAccount,
                        DESTINATION, "1.25", PUBLIC_KEY),
                "authority is too long"
        );

        byte[] negativeClockSeed = seed();
        IrohaBridgeException negativeClock = assertThrows(
                IrohaBridgeException.class,
                () -> bridge(-1).buildAndSignTransfer(validRequest("1.25", DESTINATION), negativeClockSeed)
        );
        assertTrue(negativeClock.getMessage().contains("creation time"));
        assertAllZero(negativeClockSeed);
    }

    @Test
    public void tamperingSignaturePayloadAmountDestinationOrTimeBreaksTheGoldenResult() throws Exception {
        IrohaSignedTransfer original = sign(validRequest("1.25", DESTINATION), CREATION_TIME_MILLIS);
        SignedTransaction decoded = SignedTransactionEncoder.decodeVersioned(original.getSignedTransaction());
        byte[] prehash = IrohaHash.prehash(decoded.encodedPayload());
        byte[] signature = decoded.signature();
        try {
            signature[signature.length - 1] ^= 1;
            assertFalse(verify(PUBLIC_KEY, prehash, signature));

            SignedTransaction tamperedSignature = new SignedTransaction(
                    decoded.encodedPayload(),
                    signature,
                    new byte[0],
                    decoded.schemaName()
            );
            byte[] tamperedVersioned = SignedTransactionEncoder.encodeVersioned(tamperedSignature);
            try {
                assertNotEquals(
                        original.getTransactionHashHex(),
                        IrohaTransferBridge.canonicalTransactionHashHex(tamperedVersioned)
                );
            } finally {
                Arrays.fill(tamperedVersioned, (byte) 0);
            }
        } finally {
            Arrays.fill(prehash, (byte) 0);
            Arrays.fill(signature, (byte) 0);
        }

        byte[] changedPayload = decoded.encodedPayload();
        changedPayload[changedPayload.length - 1] ^= 1;
        byte[] changedPrehash = IrohaHash.prehash(changedPayload);
        try {
            assertFalse(verify(PUBLIC_KEY, changedPrehash, decoded.signature()));
        } finally {
            Arrays.fill(changedPayload, (byte) 0);
            Arrays.fill(changedPrehash, (byte) 0);
        }

        IrohaSignedTransfer changedAmount = sign(validRequest("1.26", DESTINATION), CREATION_TIME_MILLIS);
        IrohaSignedTransfer changedDestination = sign(
                validRequest("1.25", OTHER_DESTINATION),
                CREATION_TIME_MILLIS
        );
        IrohaSignedTransfer changedTime = sign(validRequest("1.25", DESTINATION), CREATION_TIME_MILLIS + 1);
        assertNotEquals(original.getTransactionHashHex(), changedAmount.getTransactionHashHex());
        assertNotEquals(original.getTransactionHashHex(), changedDestination.getTransactionHashHex());
        assertNotEquals(original.getTransactionHashHex(), changedTime.getTransactionHashHex());
        assertFalse(Arrays.equals(original.getSignedTransaction(), changedAmount.getSignedTransaction()));
        assertFalse(Arrays.equals(original.getSignedTransaction(), changedDestination.getSignedTransaction()));
        assertFalse(Arrays.equals(original.getSignedTransaction(), changedTime.getSignedTransaction()));
    }

    @Test
    public void publicApiIsDefensiveAndExposesNoSdkKotlinOrProviderTypes() throws Exception {
        byte[] suppliedPublicKey = Arrays.copyOf(PUBLIC_KEY, PUBLIC_KEY.length);
        IrohaTransferRequest request = request(
                IrohaTransferBridge.SUPPORTED_NETWORK,
                IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                AUTHORITY,
                AUTHORITY,
                ASSET_DEFINITION,
                ASSET_DEFINITION + "#" + AUTHORITY,
                DESTINATION,
                "1.25",
                suppliedPublicKey
        );
        suppliedPublicKey[0] ^= 1;
        assertArrayEquals(PUBLIC_KEY, request.getSigningPublicKey());
        byte[] returnedPublicKey = request.getSigningPublicKey();
        assertNotSame(returnedPublicKey, request.getSigningPublicKey());
        returnedPublicKey[0] ^= 1;
        assertArrayEquals(PUBLIC_KEY, request.getSigningPublicKey());

        IrohaSignedTransfer signed = sign(request, CREATION_TIME_MILLIS);
        byte[] first = signed.getSignedTransaction();
        byte[] second = signed.getSignedTransaction();
        assertNotSame(first, second);
        first[0] = 0;
        assertEquals(1, signed.getSignedTransaction()[0]);

        Class<?>[] apiClasses = {
            IrohaBridgeException.class,
            IrohaTransferRequest.class,
            IrohaSignedTransfer.class,
            IrohaTransferBridge.class
        };
        for (Class<?> apiClass : apiClasses) {
            assertEquals(0, apiClass.getAnnotations().length);
            for (Method method : apiClass.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    assertSafeApiType(method.getReturnType());
                    for (Class<?> parameter : method.getParameterTypes()) {
                        assertSafeApiType(parameter);
                    }
                }
            }
            for (Constructor<?> constructor : apiClass.getConstructors()) {
                for (Class<?> parameter : constructor.getParameterTypes()) {
                    assertSafeApiType(parameter);
                }
            }
        }

        assertThrows(
                NullPointerException.class,
                () -> request(
                        IrohaTransferBridge.SUPPORTED_NETWORK,
                        IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                        AUTHORITY,
                        AUTHORITY,
                        ASSET_DEFINITION,
                        ASSET_DEFINITION + "#" + AUTHORITY,
                        DESTINATION,
                        "1.25",
                        null
                )
        );
    }

    @Test
    public void concurrentSigningIsDeterministicAndDoesNotShareSecretState() throws Exception {
        IrohaTransferBridge sharedBridge = bridge(CREATION_TIME_MILLIS);
        IrohaTransferRequest sharedRequest = validRequest("1.25", DESTINATION);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<IrohaSignedTransfer>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 24; index++) {
                futures.add(executor.submit(
                        () -> sharedBridge.buildAndSignTransfer(sharedRequest, seed())
                ));
            }

            IrohaSignedTransfer first = futures.get(0).get();
            for (Future<IrohaSignedTransfer> future : futures) {
                IrohaSignedTransfer result = future.get();
                assertEquals(first.getTransactionHashHex(), result.getTransactionHashHex());
                assertArrayEquals(first.getSignedTransaction(), result.getSignedTransaction());
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static IrohaTransferBridge bridge(long timeMillis) {
        return new IrohaTransferBridge(() -> timeMillis);
    }

    private static IrohaSignedTransfer sign(IrohaTransferRequest request, long timeMillis)
            throws IrohaBridgeException {
        return bridge(timeMillis).buildAndSignTransfer(request, seed());
    }

    private static IrohaTransferRequest validRequest(String amount, String destination) {
        return request(
                IrohaTransferBridge.SUPPORTED_NETWORK,
                IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                AUTHORITY,
                AUTHORITY,
                ASSET_DEFINITION,
                ASSET_DEFINITION + "#" + AUTHORITY,
                destination,
                amount,
                PUBLIC_KEY
        );
    }

    private static IrohaTransferRequest validRequestWithMetadata(
            String amount,
            String destination,
            Map<String, String> metadata
    ) {
        return new IrohaTransferRequest(
                IrohaTransferBridge.SUPPORTED_NETWORK,
                IrohaTransferBridge.SUPPORTED_CHAIN_ID,
                AUTHORITY,
                AUTHORITY,
                ASSET_DEFINITION,
                ASSET_DEFINITION + "#" + AUTHORITY,
                destination,
                amount,
                PUBLIC_KEY,
                metadata
        );
    }

    private static Map<String, String> walletSmokeMetadata() {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("evidence_role", "wallet-smoke");
        metadata.put("route_governance_action_hash", ROUTE_GOVERNANCE_ACTION_HASH);
        metadata.put("wallet_platform", "android");
        metadata.put("wallet_commit", WALLET_COMMIT);
        return metadata;
    }

    private static void assertMetadataValueRejected(
            String key,
            String value,
            String expectedMessage
    ) {
        Map<String, String> metadata = walletSmokeMetadata();
        metadata.put(key, value);
        assertMetadataRejected(metadata, expectedMessage);
    }

    private static void assertMetadataRejected(
            Map<String, String> metadata,
            String ignoredSchemaSpecificMessage
    ) {
        assertRejected(
                validRequestWithMetadata("1.25", DESTINATION, metadata),
                "Nexus-only"
        );
    }

    private static IrohaTransferRequest request(
            String network,
            String chainId,
            String authority,
            String sourceAccountId,
            String assetDefinitionId,
            String sourceAssetId,
            String destinationAccountId,
            String amount,
            byte[] publicKey
    ) {
        return new IrohaTransferRequest(
                network,
                chainId,
                authority,
                sourceAccountId,
                assetDefinitionId,
                sourceAssetId,
                destinationAccountId,
                amount,
                publicKey
        );
    }

    private static void assertRejected(IrohaTransferRequest request, String expectedMessage) {
        byte[] seed = seed();
        IrohaBridgeException error = assertThrows(
                IrohaBridgeException.class,
                () -> bridge(CREATION_TIME_MILLIS).buildAndSignTransfer(request, seed)
        );
        assertTrue(error.getMessage(), error.getMessage().contains(expectedMessage));
        assertAllZero(seed);
    }

    private static void assertAllZero(byte[] value) {
        for (byte item : value) {
            assertEquals(0, item);
        }
    }

    private static void assertCompactRejected(byte[] encoded, String expectedMessage) {
        IrohaBridgeException error = assertThrows(
                IrohaBridgeException.class,
                () -> IrohaTransferBridge.decodeCanonicalCompactLength(encoded)
        );
        assertTrue(error.getMessage(), error.getMessage().contains(expectedMessage));
    }

    private static boolean verify(byte[] publicKey, byte[] message, byte[] signature) {
        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, new Ed25519PublicKeyParameters(publicKey, 0));
        verifier.update(message, 0, message.length);
        return verifier.verifySignature(signature);
    }

    private static void assertSafeApiType(Class<?> type) {
        Class<?> component = type;
        while (component.isArray()) {
            component = component.getComponentType();
        }
        String name = component.getName();
        assertFalse(name, name.startsWith("org.hyperledger.iroha"));
        assertFalse(name, name.startsWith("org.bouncycastle"));
        assertFalse(name, name.startsWith("kotlin"));
    }

    private static byte[] seed() {
        byte[] seed = new byte[32];
        for (int index = 0; index < seed.length; index++) {
            seed[index] = (byte) index;
        }
        return seed;
    }

    private static Properties loadHashVector() {
        Properties properties = new Properties();
        try (InputStream input = IrohaTransferBridgeTest.class.getResourceAsStream(
                "/iroha-compact-hash-vector.properties"
        )) {
            if (input == null) {
                throw new AssertionError("missing compact hash vector resource");
            }
            properties.load(input);
        } catch (IOException error) {
            throw new AssertionError("failed to load compact hash vector resource", error);
        }
        return properties;
    }

    private static String vector(String key) {
        String value = HASH_VECTOR.getProperty(key);
        if (value == null || value.isEmpty()) {
            throw new AssertionError("missing compact hash vector key: " + key);
        }
        return value;
    }

    private static byte[] hex(String value) {
        byte[] decoded = new byte[value.length() / 2];
        for (int index = 0; index < decoded.length; index++) {
            decoded[index] = (byte) Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
        }
        return decoded;
    }

    private static String repeat(char value, int count) {
        char[] repeated = new char[count];
        Arrays.fill(repeated, value);
        return new String(repeated);
    }
}
