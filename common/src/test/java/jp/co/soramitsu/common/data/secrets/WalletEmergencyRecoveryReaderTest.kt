package jp.co.soramitsu.common.data.secrets

import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.WalletMasterKeyAttestation
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Emergency key access (addendum U0.4) runs every secret 4.2.0 and 3.7.x
 * stored, from the production-parity golden vectors, through the reader:
 * every one is listed with its recovery material exactly as stored, and
 * nothing on the device changes.
 */
class WalletEmergencyRecoveryReaderTest {

    private val preferences = HashMapEncryptedPreferences()

    @Test
    fun `every golden vector is readable exactly as stored`() {
        val vectors = WalletSecretVectors.family("12-validator-acceptance.json")
        val expected = vectors.mapIndexed { index, vector ->
            val metaId = index + 1L
            val key = when (vector["store"].asString) {
                "SubstrateSecrets(V3)" -> "$metaId:SUBSTRATE_SECRETS"
                "EthereumSecrets(V3)" -> "$metaId:ETHEREUM_SECRETS"
                "TonSecrets(V3)" -> "$metaId:TON_SECRETS"
                "ChainAccountSecrets(V2)" ->
                    "$metaId:${vector["accountId"].asString.removePrefix("0x")}:ACCESS_SECRETS"
                else -> error("Unexpected store ${vector["store"]}")
            }
            preferences.putEncryptedString(key, vector["stored"].asString)
            key to vector
        }.toMap()
        preferences.putEncryptedString(WalletMasterKeyAttestation.PIN_CODE_KEY, "135790")
        val before = snapshot()

        val entries = WalletEmergencyRecoveryReader(preferences).read()

        assertEquals(expected.keys, entries.map { it.storageKey }.toSet())
        entries.forEach { entry ->
            val vector = expected.getValue(entry.storageKey)
            val source = vector["source"].asString
            val types = entry.fields.map { it.type }
            assertTrue(entry.storageKey, entry.readable)
            assertEquals(EmergencyRecoveryState.ACTIVE, entry.state)
            assertFalse(entry.storageKey, EmergencyRecoveryFieldType.STORED_DATA in types)
            when {
                source.startsWith("ton-mnemonic") -> assertEquals(
                    // The text as typed, spaces and capitals included.
                    vector["storedText"].asString,
                    entry.field(EmergencyRecoveryFieldType.TON_PHRASE)
                )

                source == "mnemonic" || source == "chain-mnemonic" -> {
                    val phrase = checkNotNull(entry.field(EmergencyRecoveryFieldType.RECOVERY_PHRASE))
                    assertTrue(phrase.split(" ").size in setOf(12, 15, 18, 21, 24))
                    MnemonicCreator.fromWords(phrase)
                }

                source == "raw-seed" || source == "chain-seed" ->
                    assertTrue(EmergencyRecoveryFieldType.RAW_SEED in types)
            }
            assertTrue(
                entry.storageKey,
                types.any {
                    it == EmergencyRecoveryFieldType.PRIVATE_KEY ||
                        it == EmergencyRecoveryFieldType.ETHEREUM_PRIVATE_KEY ||
                        it == EmergencyRecoveryFieldType.TON_PRIVATE_KEY
                }
            )
        }
        assertEquals(before, snapshot())
    }

    @Test
    fun `entropy renders the phrase 4_2_0 rendered from it`() {
        WalletSecretVectors.family("02-bip39.json")
            .filter { it["kind"].asString == "fromEntropy" }
            .forEach { vector ->
                val local = HashMapEncryptedPreferences()
                local.putEncryptedString(
                    "1:SUBSTRATE_SECRETS",
                    SubstrateSecrets(
                        substrateKeyPair = Keypair(publicKey = ByteArray(32) { 1 }, privateKey = ByteArray(32) { 2 }),
                        entropy = vector["entropy"].asString.fromHex()
                    ).toHexString()
                )

                val entry = WalletEmergencyRecoveryReader(local).read().single()

                assertEquals(
                    vector["words"].asString,
                    entry.field(EmergencyRecoveryFieldType.RECOVERY_PHRASE)
                )
            }
    }

    @Test
    fun `keys set aside or archived are listed with where they are`() {
        val vector = WalletSecretVectors.family("12-validator-acceptance.json")
            .first { it["store"].asString == "TonSecrets(V3)" }
        val stored = vector["stored"].asString
        preferences.putEncryptedString(WalletSecretQuarantine.keyFor("4:TON_SECRETS"), stored)
        preferences.putEncryptedString(WalletSecretArchive.keyFor("9:TON_SECRETS"), stored)
        preferences.putEncryptedString(WalletSecretArchive.keyFor("9:TON_SECRETS", slot = 1), stored)

        val entries = WalletEmergencyRecoveryReader(preferences).read()

        assertEquals(
            listOf(
                Triple(4L, EmergencyRecoveryState.SET_ASIDE, EmergencyRecoveryKeyKind.TON),
                Triple(9L, EmergencyRecoveryState.ARCHIVED, EmergencyRecoveryKeyKind.TON),
                Triple(9L, EmergencyRecoveryState.ARCHIVED, EmergencyRecoveryKeyKind.TON)
            ),
            entries.map { Triple(it.walletId, it.state, it.kind) }
        )
        entries.forEach {
            assertEquals(vector["storedText"].asString, it.field(EmergencyRecoveryFieldType.TON_PHRASE))
        }
    }

    @Test
    fun `unreadable and unrecognised values are shown as they are, never dropped`() {
        // An empty value is what this device returns for ciphertext it can
        // no longer decrypt (audit AND-V3).
        preferences.putEncryptedString("3:SUBSTRATE_SECRETS", "")
        preferences.putEncryptedString("3:ETHEREUM_SECRETS", "not a SCALE payload")

        val entries = WalletEmergencyRecoveryReader(preferences).read()

        val ethereum = entries.single { it.storageKey == "3:ETHEREUM_SECRETS" }
        assertEquals(
            listOf(EmergencyRecoveryField(EmergencyRecoveryFieldType.STORED_DATA, "not a SCALE payload")),
            ethereum.fields
        )
        val substrate = entries.single { it.storageKey == "3:SUBSTRATE_SECRETS" }
        assertFalse(substrate.readable)
        assertTrue(substrate.fields.isEmpty())
    }

    @Test
    fun `only wallet keys are listed`() {
        preferences.putEncryptedString(WalletMasterKeyAttestation.PIN_CODE_KEY, "135790")
        preferences.putEncryptedString(
            TonConnectStorageKeys.scoped(metaId = 1L, url = "https://dapp.example", source = "WEB"),
            "session"
        )
        preferences.putEncryptedString("1:SOMETHING_ELSE", "value")
        preferences.putEncryptedString(WalletSecretQuarantine.keyFor("1:SOMETHING_ELSE"), "value")

        assertTrue(WalletEmergencyRecoveryReader(preferences).read().isEmpty())
        assertEquals(0, WalletEmergencyRecoveryReader(preferences).storedKeyCount())
        preferences.putEncryptedString("1:TON_SECRETS", "")
        assertEquals(1, WalletEmergencyRecoveryReader(preferences).storedKeyCount())
    }

    private fun EmergencyRecoveryEntry.field(type: EmergencyRecoveryFieldType): String? =
        fields.firstOrNull { it.type == type }?.value

    private fun snapshot(): Map<String, String?> {
        val keys = preferences.keysWithPrefixes(
            prefixes = (0..9).map(Int::toString).toSet() + setOf("pin_code", "wallet_"),
            maxResultCount = 8_192,
            maxKeyBytes = 1_024,
            maxTotalKeyBytes = 1_048_576
        )
        return keys.associateWith(preferences::getDecryptedString)
    }
}
