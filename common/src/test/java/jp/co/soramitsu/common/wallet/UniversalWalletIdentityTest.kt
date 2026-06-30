package jp.co.soramitsu.common.wallet

import com.google.gson.Gson
import jp.co.soramitsu.common.model.UniversalWalletDerivationPaths
import jp.co.soramitsu.common.model.UniversalWalletEcosystem
import jp.co.soramitsu.common.model.UniversalWalletIdentity
import jp.co.soramitsu.common.model.UniversalWalletIdentity.UniversalWalletPublicAccount
import jp.co.soramitsu.common.model.UniversalWalletIdentity.UniversalWalletSource
import jp.co.soramitsu.common.model.UniversalWalletIdentity.UniversalWalletStatus
import jp.co.soramitsu.common.model.UniversalWalletIdentity.ValidationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalWalletIdentityTest {

    @Test
    fun `validates and serializes active universal wallet identity`() {
        val identity = activeIdentity()

        assertTrue(identity.validationErrors().isEmpty())
        assertEquals(identity, identity.requireValid())

        val json = Gson().toJson(identity)
        assertTrue(json.contains("\"schemaVersion\":2"))
        assertTrue(json.contains("\"source\":\"created-24-word\""))
        assertTrue(json.contains("\"status\":\"active\""))
        assertTrue(json.contains("\"ecosystem\":\"bitcoin\""))
    }

    @Test
    fun `rejects partial active wallets and duplicate accounts`() {
        val partial = activeIdentity(
            accounts = defaultAccounts().filterNot { it.ecosystem == UniversalWalletEcosystem.Iroha.id }
        )
        val duplicate = activeIdentity(
            accounts = defaultAccounts() + defaultAccounts().first().copy(address = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306abc")
        )

        assertTrue(partial.validationErrors().contains(ValidationError.MissingActiveEcosystem))
        assertTrue(duplicate.validationErrors().contains(ValidationError.DuplicateAccountId))
    }

    @Test
    fun `rejects malformed identity and account fields`() {
        val account = defaultAccounts().first().copy(
            accountId = "../bad",
            ecosystem = "unknown",
            address = " bc1bad ",
            chainId = "../bad",
            derivationPath = "m/44'/0'/x",
            publicKeyHex = "ABC"
        )
        val identity = activeIdentity(
            walletId = "bad",
            displayName = "bad\u0000name",
            createdAtMillis = 10,
            updatedAtMillis = 9,
            accounts = listOf(account)
        )

        val errors = identity.validationErrors()

        assertTrue(errors.contains(ValidationError.InvalidWalletId))
        assertTrue(errors.contains(ValidationError.InvalidDisplayName))
        assertTrue(errors.contains(ValidationError.InvalidTimestamps))
        assertTrue(errors.contains(ValidationError.InvalidAccountId))
        assertTrue(errors.contains(ValidationError.InvalidEcosystem))
        assertTrue(errors.contains(ValidationError.InvalidAddress))
        assertTrue(errors.contains(ValidationError.InvalidChainId))
        assertTrue(errors.contains(ValidationError.InvalidDerivationPath))
        assertTrue(errors.contains(ValidationError.InvalidPublicKeyHex))
        assertThrows(UniversalWalletIdentity.UniversalWalletIdentityException::class.java) {
            identity.requireValid()
        }
    }

    @Test
    fun `enforces legacy export only source and reason`() {
        val legacy = activeIdentity(
            source = UniversalWalletSource.LegacyImport,
            status = UniversalWalletStatus.LegacyExportOnly,
            accounts = listOf(defaultAccounts().first()),
            legacyExportOnlyReason = "pre-cutoff account export"
        )
        val wrongSource = legacy.copy(source = UniversalWalletSource.Created24Word)
        val missingReason = legacy.copy(legacyExportOnlyReason = " ")
        val activeWithReason = activeIdentity(legacyExportOnlyReason = "not allowed")

        assertTrue(legacy.validationErrors().isEmpty())
        assertTrue(wrongSource.validationErrors().contains(ValidationError.InvalidLegacySource))
        assertTrue(missingReason.validationErrors().contains(ValidationError.LegacyReasonRequired))
        assertTrue(activeWithReason.validationErrors().contains(ValidationError.LegacyReasonNotAllowed))
    }

    @Test
    fun `allows migration-required identity to be partial but not empty`() {
        val migrating = activeIdentity(
            status = UniversalWalletStatus.MigrationRequired,
            accounts = listOf(defaultAccounts().first())
        )
        val emptyMigrating = migrating.copy(publicAccounts = emptyList())

        assertFalse(migrating.validationErrors().contains(ValidationError.MissingActiveEcosystem))
        assertTrue(migrating.validationErrors().isEmpty())
        assertTrue(emptyMigrating.validationErrors().contains(ValidationError.PublicAccountsRequired))
    }

    private fun activeIdentity(
        walletId: String = "uw2_1234567890abcdef",
        displayName: String = "Fearless Universal",
        source: UniversalWalletSource = UniversalWalletSource.Created24Word,
        status: UniversalWalletStatus = UniversalWalletStatus.Active,
        accounts: List<UniversalWalletPublicAccount> = defaultAccounts(),
        createdAtMillis: Long = 1_710_000_000_000,
        updatedAtMillis: Long? = null,
        legacyExportOnlyReason: String? = null
    ) = UniversalWalletIdentity(
        walletId = walletId,
        displayName = displayName,
        source = source,
        status = status,
        publicAccounts = accounts,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        legacyExportOnlyReason = legacyExportOnlyReason
    )

    private fun defaultAccounts() = listOf(
        UniversalWalletPublicAccount(
            accountId = "substrate-polkadot",
            ecosystem = UniversalWalletEcosystem.Substrate,
            address = "15FKRtF6nX3SE4PaW4LX69XqYHq2oSep9JX5KF4cgN1XkZ4q",
            chainId = "polkadot",
            derivationPath = UniversalWalletDerivationPaths.SUBSTRATE_ROOT,
            publicKeyHex = HEX_32
        ),
        UniversalWalletPublicAccount(
            accountId = "evm-default",
            ecosystem = UniversalWalletEcosystem.Evm,
            address = "0x1111111111111111111111111111111111111111",
            chainId = "eip155:1",
            derivationPath = UniversalWalletDerivationPaths.EVM_DEFAULT
        ),
        UniversalWalletPublicAccount(
            accountId = "bitcoin-mainnet",
            ecosystem = UniversalWalletEcosystem.Bitcoin,
            address = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu",
            chainId = "bitcoin:mainnet",
            derivationPath = UniversalWalletDerivationPaths.BITCOIN_MAINNET_FIRST_RECEIVE
        ),
        UniversalWalletPublicAccount(
            accountId = "solana-mainnet",
            ecosystem = UniversalWalletEcosystem.Solana,
            address = "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk",
            chainId = "solana:mainnet",
            derivationPath = UniversalWalletDerivationPaths.SOLANA_DEFAULT,
            publicKeyHex = HEX_32
        ),
        UniversalWalletPublicAccount(
            accountId = "ton-mainnet",
            ecosystem = UniversalWalletEcosystem.Ton,
            address = "UQDxAUFadQXDd3EXGa3TLF_EF66gMc9h3_aZ0j0zXNoIYUCc",
            chainId = "ton:mainnet",
            derivationPath = UniversalWalletDerivationPaths.TON_DEFAULT,
            publicKeyHex = HEX_32
        ),
        UniversalWalletPublicAccount(
            accountId = "iroha-taira",
            ecosystem = UniversalWalletEcosystem.Iroha,
            address = "testuﾛ1Pcﾅ2ﾗtﾉaﾘLﾕｽ2MヱﾐﾎｳﾓヱﾇﾆｲMﾒSﾏﾑヱﾇJヱFmJﾇMs6YN687Y",
            chainId = "iroha3-taira",
            derivationPath = UniversalWalletDerivationPaths.IROHA_DEFAULT,
            publicKeyHex = HEX_32
        )
    )

    private companion object {
        val HEX_32 = "11".repeat(32)
    }
}
