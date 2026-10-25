package jp.co.soramitsu.common.data.secrets

import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v1.SourceInternal
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchivedWalletMaterialDecoderTest {
    private val decoder = ArchivedWalletMaterialDecoder()

    @Test fun everyFrozenReleasedNonNativeSigningKeyCanBePreviewedWithItsOriginalIdentity() {
        val vectors = WalletSecretVectors.family("12-validator-acceptance.json")
            .filterNot { it.get("cryptoType")?.asString == "SR25519" }
        assertEquals(155, vectors.size)
        vectors.forEach { vector ->
            val name = vector["case"].asString
            val source = when (vector["store"].asString) {
                "SubstrateSecrets(V3)" -> "7:SUBSTRATE_SECRETS"
                "EthereumSecrets(V3)" -> "7:ETHEREUM_SECRETS"
                "TonSecrets(V3)" -> "7:TON_SECRETS"
                else -> "7:${vector["accountId"].asString.removePrefix("0x")}:ACCESS_SECRETS"
            }
            val decoded = decoder.decode(source, vector["stored"].asString)
            assertEquals(name, 0, decoded.unprovedParts)
            assertEquals(name, 1, decoded.candidates.size)
            assertEquals(name, vector["publicKey"].asString.removePrefix("0x"), Hex.toHexString(decoded.candidates.single().publicKey))
            if (source.endsWith("TON_SECRETS")) {
                assertEquals(name, vector["stored"].asString, decoded.candidates.single().encoded)
            }
        }
    }

    @Test fun legacyAccessProvesEachKeyIndependentlyAndDoesNotInventATonPhrase() {
        val valid = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 3 })
        val invalid = Keypair(valid.publicKey, ByteArray(32))
        val encoded = MetaAccountSecrets(substrateKeyPair = invalid, ethereumKeypair = valid).toHexString()
        val decoded = decoder.decode("7:ACCESS_SECRETS", encoded)
        assertEquals(1, decoded.unprovedParts)
        assertEquals(WalletSecretScope.Ethereum, decoded.candidates.single().scope)
        assertTrue(decoded.candidates.single().encoded.isNotEmpty())
    }

    @Test fun sixFieldAccessAndV04RetainedKeysKeepTheirShippedIdentity() {
        val pair = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 4 })
        val current = MetaAccountSecrets(substrateKeyPair = pair, ethereumKeypair = pair).toHexString()
        // The six-field shipped schema is exactly the seven-field schema without its absent TON option.
        val sixFields = current.dropLast(2)
        val access = decoder.decode("7:ACCESS_SECRETS", sixFields)
        assertEquals(0, access.unprovedParts)
        assertEquals(setOf(WalletSecretScope.Substrate, WalletSecretScope.Ethereum), access.candidates.map { it.scope }.toSet())
        // Frozen 0.4.x layout: compact private vector, compact public vector, optional nonce.
        val legacy = "0x" + Hex.toHexString(byteArrayOf((pair.privateKey.size shl 2).toByte()) + pair.privateKey +
            byteArrayOf((pair.publicKey.size shl 2).toByte()) + pair.publicKey + byteArrayOf(0))
        for (key in listOf("private_public-fixture", "legacy_v04_public-fixture")) {
            val decoded = decoder.decode(key, legacy)
            assertEquals(0, decoded.unprovedParts)
            assertEquals(Hex.toHexString(pair.publicKey), Hex.toHexString(decoded.candidates.single().publicKey))
        }
    }

    @Test fun oldAccessTonKeyRestoresWithoutInventingRecoveryMaterial() {
        val vector = WalletSecretVectors.family("12-validator-acceptance.json").first { it["store"].asString == "TonSecrets(V3)" }
        val ton = TonSecrets.read(vector["stored"].asString)
        val pair = Keypair(ton[TonSecrets.PublicKey], ton[TonSecrets.PrivateKey])
        val invalid = Keypair(ByteArray(32), ByteArray(32))
        val stored = MetaAccountSecrets(substrateKeyPair = invalid, tonKeypair = pair).toHexString()
        val decoded = decoder.decode("7:ACCESS_SECRETS", stored)
        assertEquals(1, decoded.unprovedParts)
        val candidate = decoded.candidates.single()
        assertEquals(WalletSecretScope.Ton, candidate.scope)
        assertTrue(TonSecrets.read(candidate.encoded)[TonSecrets.Seed].isEmpty())
        assertEquals(vector["publicKey"].asString.removePrefix("0x"), Hex.toHexString(candidate.publicKey))
    }

    @Test fun legacySourceRestoresItsSigningKeyWithoutTrustingItsWrongPhrase() {
        val pair = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 4 })
        val source = SourceInternal.read(SOURCE_TEMPLATE)
        source[SourceInternal.Type] = "JSON"
        source[SourceInternal.PrivateKey] = pair.privateKey
        source[SourceInternal.PublicKey] = pair.publicKey
        source[SourceInternal.Mnemonic] = "not the phrase for this key"
        val decoded = decoder.decode("security_source_legacy-address", source.toHexString())
        assertEquals(0, decoded.unprovedParts)
        assertEquals(Hex.toHexString(pair.publicKey), Hex.toHexString(decoded.candidates.single().publicKey))
        assertFalse(decoded.candidates.single().toString().contains(source.toHexString()))
    }

    @Test fun unknownMalformedAndWrongNetworkIdentityAreRetainedAsUnproved() {
        for (source in listOf("unrecognized", "7:SUBSTRATE_SECRETS", "private_test-address", "7:ACCESS_SECRETS")) {
            val decoded = decoder.decode(source, "not a valid encoded record")
            assertTrue(decoded.candidates.isEmpty())
            assertTrue(decoded.unprovedParts > 0)
        }
        val vector = WalletSecretVectors.family("12-validator-acceptance.json").first {
            it["store"].asString == "ChainAccountSecrets(V2)" && it["cryptoType"].asString != "SR25519"
        }
        val decoded = decoder.decode("7:${"00".repeat(32)}:ACCESS_SECRETS", vector["stored"].asString)
        assertTrue(decoded.candidates.isEmpty())
        assertEquals(1, decoded.unprovedParts)
    }

    @Test fun recoveryDriftDoesNotBlockAProvenStoredSubstrateKey() {
        val pair = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 5 })
        val source = SubstrateSecrets(pair, seed = ByteArray(32) { 6 }).toHexString()
        val decoded = decoder.decode("7:SUBSTRATE_SECRETS", source)
        assertEquals(1, decoded.candidates.size)
        assertEquals(source, decoded.candidates.single().encoded)
        assertEquals(0, decoded.unprovedParts)
    }

    private companion object {
        // SourceInternal has three leading SCALE vectors, then four optional fields.
        const val SOURCE_TEMPLATE = "00000000000000"
    }
}
