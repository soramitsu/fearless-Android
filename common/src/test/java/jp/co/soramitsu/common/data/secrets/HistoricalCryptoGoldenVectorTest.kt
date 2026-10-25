package jp.co.soramitsu.common.data.secrets

import com.google.gson.Gson
import com.google.gson.JsonObject
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A3: the RC replaced the private production crypto library every historical
 * release signed with. For each historical library version with golden
 * vectors, the RC's own primitives (the ones `WalletRootSecretValidator`,
 * the chain-account validator and the import flows call) must reproduce the
 * historical outputs exactly: BIP39, seeds, junction decoding, ed25519,
 * ecdsa and Ethereum keys, account ids, signatures and JSON keyring import.
 *
 * sr25519 needs the shipped native library and is covered on a device by
 * HistoricalSr25519GoldenVectorTest. SS58 and EIP-55 are additionally
 * checked against polkadot-js, an implementation unrelated to both libraries.
 */
@RunWith(Parameterized::class)
class HistoricalCryptoGoldenVectorTest(private val library: String) {

    private val vectors: JsonObject = CryptoGoldenVectors.crypto(library)

    @Test
    fun bip39MatchesHistoricalLibrary() {
        val mismatches = GoldenMismatches(library, "bip39")
        vectors.objects("bip39").forEach { vector ->
            when (vector.string("kind")) {
                "fromEntropy" -> mismatches.check("MnemonicCreator.fromEntropy", vector.string("entropy")) {
                    val entropy = vector.bytes("entropy")
                    expectEqual(vector.string("words"), MnemonicCreator.fromEntropy(entropy.copyOf()).words, "words")
                }

                "fromWords" -> mismatches.check("MnemonicCreator.fromWords", vector.string("case")) {
                    val historical = vector.getAsJsonObject("prod")
                    val actual = runCatching { MnemonicCreator.fromWords(vector.string("input")) }
                    if (historical.has("error")) {
                        // 4.2.0 threw StringIndexOutOfBounds for empty input where the RC throws
                        // Bip39Exception; a phrase historical code rejected must stay rejected.
                        expectEqual(true, actual.isFailure, "rejected")
                    } else {
                        val mnemonic = actual.getOrThrow()
                        expectEqual(historical.string("words"), mnemonic.words, "words")
                        expectEqual(historical.string("entropy"), mnemonic.entropy.hex(), "entropy")
                    }
                }

                "fromEntropy-invalid-length" -> mismatches.check("MnemonicCreator.fromEntropy", "size=${vector["size"]}") {
                    val actual = runCatching { MnemonicCreator.fromEntropy(ByteArray(vector["size"].asInt)) }
                    expectEqual(true, actual.isFailure, "rejected")
                }

                else -> error("Unknown bip39 vector kind ${vector.string("kind")}")
            }
        }
        mismatches.assertNone(minimumChecked = 60)
    }

    @Test
    fun seedsMatchHistoricalLibrary() {
        val mismatches = GoldenMismatches(library, "seeds")
        vectors.objects("seeds").forEach { vector ->
            val mnemonic = vector.string("mnemonic")
            val password = vector.nullableString("password")
            mismatches.check("SubstrateSeedFactory.deriveSeed", "$mnemonic / $password") {
                expectEqual(
                    vector.string("substrateSeed"),
                    SubstrateSeedFactory.deriveSeed(mnemonic, password).seed.hex(),
                    "Substrate seed"
                )
                // 4.2.0 stored the first 32 bytes (FearlessLibExt.deriveSeed32).
                expectEqual(
                    vector.string("substrateSeed").fromHex().copyOf(32).hex(),
                    SubstrateSeedFactory.deriveSeed32(mnemonic, password).seed.hex(),
                    "Substrate seed32"
                )
            }
            mismatches.check("EthereumSeedFactory.deriveSeed", "$mnemonic / $password") {
                expectEqual(
                    vector.string("ethereumSeed"),
                    EthereumSeedFactory.deriveSeed(mnemonic, password).seed.hex(),
                    "Ethereum seed"
                )
            }
        }
        mismatches.assertNone(minimumChecked = 200)
    }

    @Test
    fun junctionDecodingMatchesHistoricalLibrary() {
        val mismatches = GoldenMismatches(library, "junctions")
        vectors.objects("junctions").forEach { vector ->
            val decoder: JunctionDecoder = when (vector.string("decoder")) {
                "substrate" -> SubstrateJunctionDecoder
                "bip32" -> BIP32JunctionDecoder
                else -> error("Unknown decoder ${vector.string("decoder")}")
            }
            val path = vector.string("path")
            mismatches.check("${vector.string("decoder")}.decode", path) {
                val expected = vector.getAsJsonObject("expected")
                val actual = runCatching { decoder.decode(path) }
                if (expected.has("error")) {
                    expectEqual(expected.string("error"), actual.exceptionOrNull()?.libraryRelativeName(), "error")
                } else {
                    val decoded = actual.getOrThrow()
                    expectEqual(expected.nullableString("password"), decoded.password, "password")
                    expectEqual(
                        expected.objects("junctions").map { it.string("type") to it.string("chaincode") },
                        decoded.junctions.map { it.type.name to it.chaincode.hex() },
                        "junctions"
                    )
                }
            }
        }
        mismatches.assertNone(minimumChecked = 30)
    }

    @Test
    fun keypairsAndAccountIdsMatchHistoricalLibrary() {
        val mismatches = GoldenMismatches(library, "keypairs")
        vectors.objects("keypairs").forEach { vector ->
            when (vector.string("kind")) {
                "substrate" -> mismatches.check(
                    "SubstrateKeypairFactory.generate",
                    "${vector.string("cryptoType")} ${vector.string("seed")} '${vector.string("path")}'"
                ) {
                    expectKeypairOrError(vector.getAsJsonObject("expected")) {
                        SubstrateKeypairFactory.generate(
                            encryptionType = EncryptionType.valueOf(vector.string("cryptoType")),
                            seed = vector.bytes("seed"),
                            junctions = substrateJunctions(vector.string("path"))
                        )
                    }
                }

                "ethereum" -> mismatches.check(
                    "EthereumKeypairFactory.generate",
                    "${vector.string("mnemonic")} ${vector.string("path")}"
                ) {
                    expectKeypairOrError(vector.getAsJsonObject("expected")) { ethereumKeypair(vector) }
                }

                "ethereum-private-key" -> mismatches.check(
                    "EthereumKeypairFactory.createWithPrivateKey",
                    vector.string("privateKey")
                ) {
                    val keypair = EthereumKeypairFactory.createWithPrivateKey(vector.bytes("privateKey"))
                    expectKeypair(vector.getAsJsonObject("expected"), keypair)
                    expectEqual(vector.string("address"), keypair.publicKey.ethereumAddressFromPublicKey().hex(), "address")
                }

                "anchor" -> mismatches.check("anchor", "${vector.string("mnemonic")} ${vector.string("path")}") {
                    expectEqual(
                        vector.string("ethereumAddress"),
                        ethereumKeypair(vector).publicKey.ethereumAddressFromPublicKey().hex(),
                        "Ethereum address"
                    )
                }

                "substrateAccountId" -> mismatches.check("substrateAccountId", vector.string("publicKey")) {
                    expectEqual(vector.string("accountId"), vector.bytes("publicKey").substrateAccountId().hex(), "account id")
                }

                else -> error("Unknown keypair vector kind ${vector.string("kind")}")
            }
        }
        mismatches.assertNone(minimumChecked = 290)
    }

    @Test
    fun deterministicSignaturesMatchHistoricalLibrary() {
        val mismatches = GoldenMismatches(library, "signatures")
        vectors.objects("signatures").forEach { vector ->
            val cryptoType = vector.string("cryptoType")
            mismatches.check("Signer.sign", "$cryptoType ${vector.string("message").take(18)}") {
                val (encryption, keypair) = when (cryptoType) {
                    "ETHEREUM" -> MultiChainEncryption.Ethereum to
                        EthereumKeypairFactory.createWithPrivateKey(vector.bytes("privateKey"))

                    else -> {
                        val type = EncryptionType.valueOf(cryptoType)
                        MultiChainEncryption.Substrate(type) to
                            SubstrateKeypairFactory.generate(type, vector.bytes("seed"), emptyList())
                    }
                }
                val signature = Signer.sign(encryption, vector.bytes("message"), keypair).signature
                expectEqual(vector.string("signature"), signature.hex(), "signature")
            }
        }
        mismatches.assertNone(minimumChecked = 18)
    }

    @Test
    fun jsonKeyringImportMatchesHistoricalLibrary() {
        val decoder = JsonSeedDecoder(Gson())
        val mismatches = GoldenMismatches(library, "json_keyring")
        vectors.objects("json_keyring").forEach { vector ->
            val json = vector.string("json")
            val password = vector.string("password")
            mismatches.check("JsonSeedDecoder.decode", vector.string("input")) {
                val expected = vector.getAsJsonObject("expected")
                val actual = runCatching { decoder.decode(json, password) }
                if (expected.has("error")) {
                    expectEqual(expected.string("error"), actual.exceptionOrNull()?.libraryRelativeName(), "error")
                } else {
                    val imported = actual.getOrThrow()
                    val encryption = when (val chain = imported.multiChainEncryption) {
                        MultiChainEncryption.Ethereum -> "ethereum"
                        is MultiChainEncryption.Substrate -> chain.encryptionType.name
                    }
                    expectEqual(expected.string("encryption"), encryption, "encryption")
                    expectKeypair(expected.getAsJsonObject("keypair"), imported.keypair)
                    expectEqual(expected.nullableString("seed"), imported.seed.hex(), "seed")
                    expectEqual(expected.nullableString("username"), imported.username, "username")
                }
            }
            vector.getAsJsonObject("expectedWrongPassword")?.let { wrong ->
                mismatches.check("JsonSeedDecoder.decode(wrong password)", vector.string("input")) {
                    val actual = runCatching { decoder.decode(json, "$password-wrong") }
                    expectEqual(wrong.string("error"), actual.exceptionOrNull()?.libraryRelativeName(), "error")
                }
            }
        }
        mismatches.assertNone(minimumChecked = 8)
    }

    @Test
    fun ss58AndEthereumAddressesMatchAnIndependentImplementation() {
        val anchors = vectors.getAsJsonObject("independent_anchors")
        val mismatches = GoldenMismatches(library, "independent_anchors")
        anchors.objects("ss58").forEach { vector ->
            val prefix = vector["prefix"].asShort
            mismatches.check("SS58Encoder", "${vector.string("publicKey")} prefix $prefix") {
                val accountId = vector.bytes("publicKey").substrateAccountId()
                expectEqual(vector.string("accountId"), accountId.hex(), "account id")
                expectEqual(vector.string("address"), accountId.toAddress(prefix), "address")
                expectEqual(vector.string("accountId"), vector.string("address").toAccountId().hex(), "decoded account id")
            }
        }
        anchors.objects("ethereum_eip55").forEach { vector ->
            mismatches.check("ethereumAddressFromPublicKey", vector.string("publicKey")) {
                // Android stores and compares the 20 address bytes; EIP-55 casing is display only.
                expectEqual(
                    vector.string("eip55").lowercase(),
                    vector.bytes("publicKey").ethereumAddressFromPublicKey().hex(),
                    "address"
                )
            }
        }
        val prefixes = anchors.objects("ss58").map { it["prefix"].asInt }.toSet()
        assertEquals(setOf(0, 2, 42, 69), prefixes)
        mismatches.assertNone(minimumChecked = 32)
    }

    @Test
    fun sr25519RowsLiveInTheDeviceVectorFile() {
        assertTrue(vectors.objects("keypairs").none { it.nullableString("cryptoType") == "SR25519" })
        assertTrue(vectors.objects("json_keyring").none { it.getAsJsonObject("expected").nullableString("encryption") == "SR25519" })
        assertEquals(
            "core-db/src/androidTest/assets/crypto-golden/$library-sr25519.json",
            vectors.string("sr25519_rows")
        )
    }

    private fun substrateJunctions(path: String) =
        if (path.isEmpty()) emptyList() else SubstrateJunctionDecoder.decode(path).junctions

    private fun ethereumKeypair(vector: JsonObject): Keypair {
        val decoded = BIP32JunctionDecoder.decode(vector.string("path"))
        val seed = EthereumSeedFactory.deriveSeed32(vector.string("mnemonic"), decoded.password).seed
        return EthereumKeypairFactory.generate(seed = seed, junctions = decoded.junctions)
    }

    /**
     * Historical code rejected some inputs (soft junctions for ed25519/ecdsa,
     * malformed paths). The RC must reject the same inputs, with the same
     * library exception where history threw one. Where history failed with a
     * plain JVM exception (4.2.0 hit a ClassCastException on malformed BIP32
     * paths) the RC's typed decoding error is the better answer; only the
     * rejection itself is required.
     */
    private fun expectKeypairOrError(expected: JsonObject, derive: () -> Keypair) {
        val actual = runCatching(derive)
        if (expected.has("error")) {
            val historical = expected.string("error")
            if (historical.startsWith("java.")) {
                expectEqual(true, actual.isFailure, "rejected (historically $historical)")
            } else {
                expectEqual(historical, actual.exceptionOrNull()?.libraryRelativeName(), "error")
            }
        } else {
            expectKeypair(expected, actual.getOrThrow())
        }
    }

    private fun expectKeypair(expected: JsonObject, actual: Keypair) {
        expectEqual(expected.string("privateKey"), actual.privateKey.hex(), "private key")
        expectEqual(expected.string("publicKey"), actual.publicKey.hex(), "public key")
        expectEqual(expected.nullableString("nonce"), (actual as? Sr25519Keypair)?.nonce.hex(), "nonce")
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun libraries(): List<String> = CryptoGoldenVectors.LIBRARIES
    }
}
