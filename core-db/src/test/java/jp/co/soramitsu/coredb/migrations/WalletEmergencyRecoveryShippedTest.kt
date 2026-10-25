package jp.co.soramitsu.coredb.migrations

import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryFieldType
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryKeyKind
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryState
import jp.co.soramitsu.common.data.secrets.WalletEmergencyRecoveryReader
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Emergency key access from the payloads 3.7.4 wrote (verbatim writer in
 * `ShippedSecretWriters`), wherever an upgrade left them: still active,
 * set aside by an internal build, or archived after conversion. The raw seed
 * the user imported and the private key come back exactly as stored.
 */
class WalletEmergencyRecoveryShippedTest {

    @Test
    fun rawSeedImportOf374IsShownWhereverItIs() {
        val wallet = ShippedWalletFixtures.rawSeedWithEthereum()
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString("5:ACCESS_SECRETS", wallet.accessSecretsHex)
            putEncryptedString(WalletSecretQuarantine.keyFor("6:ACCESS_SECRETS"), wallet.accessSecretsHex)
            putEncryptedString(WalletSecretArchive.keyFor("7:ACCESS_SECRETS"), wallet.accessSecretsHex)
        }

        val entries = WalletEmergencyRecoveryReader(preferences).read()

        assertEquals(
            listOf(
                5L to EmergencyRecoveryState.ACTIVE,
                6L to EmergencyRecoveryState.SET_ASIDE,
                7L to EmergencyRecoveryState.ARCHIVED
            ),
            entries.map { it.walletId to it.state }
        )
        entries.forEach { entry ->
            assertEquals(EmergencyRecoveryKeyKind.LEGACY_WALLET, entry.kind)
            val fields = entry.fields.associate { it.type to it.value }
            assertEquals(ShippedWalletFixtures.SEED_HEX, fields[EmergencyRecoveryFieldType.RAW_SEED])
            assertEquals(
                "0x" + wallet.substrateKeypair.privateKey.toHexString(withPrefix = false),
                fields[EmergencyRecoveryFieldType.PRIVATE_KEY]
            )
            assertEquals(
                ShippedWalletFixtures.ETH_SEED_HEX,
                fields[EmergencyRecoveryFieldType.ETHEREUM_PRIVATE_KEY]
            )
        }
    }
}
