package jp.co.soramitsu.common.wallet

import com.google.gson.Gson
import jp.co.soramitsu.common.model.UniversalWalletEcosystem
import jp.co.soramitsu.common.model.UniversalWalletLegacyVaultDescriptor
import jp.co.soramitsu.common.model.UniversalWalletMigrationPlatform
import jp.co.soramitsu.common.model.UniversalWalletMigrationRequiredAction
import jp.co.soramitsu.common.model.UniversalWalletMigrationSnapshot
import jp.co.soramitsu.common.model.UniversalWalletMigrationValidationError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalWalletMigrationContractTest {

    @Test
    fun `blocks normal access when legacy vaults exist without a universal wallet`() {
        val snapshot = snapshot(hasUniversalWallet = false, legacyVaults = listOf(legacyVault()))

        assertTrue(snapshot.validationErrors().isEmpty())
        assertTrue(snapshot.requiredAction() == UniversalWalletMigrationRequiredAction.MigrateBeforeAccess)
        assertFalse(snapshot.allowsNormalWalletAccess())
        assertTrue(snapshot.allowsLegacySecretExport())

        val json = Gson().toJson(snapshot)
        assertTrue(json.contains("\"platform\":\"android\""))
        assertTrue(json.contains("\"mode\":\"export-only\""))
        assertTrue(json.contains("\"canSignTransactions\":false"))
    }

    @Test
    fun `requires a new universal wallet when no wallet material exists`() {
        val snapshot = snapshot(hasUniversalWallet = false, legacyVaults = emptyList())

        assertTrue(snapshot.validationErrors().isEmpty())
        assertTrue(snapshot.requiredAction() == UniversalWalletMigrationRequiredAction.CreateUniversalWallet)
        assertFalse(snapshot.allowsNormalWalletAccess())
        assertFalse(snapshot.allowsLegacySecretExport())
    }

    @Test
    fun `allows normal access once a universal wallet exists while keeping legacy export-only`() {
        val snapshot = snapshot(hasUniversalWallet = true, legacyVaults = listOf(legacyVault()))

        assertTrue(snapshot.validationErrors().isEmpty())
        assertTrue(snapshot.requiredAction() == UniversalWalletMigrationRequiredAction.NormalAccess)
        assertTrue(snapshot.allowsNormalWalletAccess())
        assertTrue(snapshot.allowsLegacySecretExport())
    }

    @Test
    fun `rejects malformed migration snapshots and legacy vaults`() {
        val badVault = legacyVault().copy(
            vaultId = "bad",
            accountId = "../bad",
            ecosystem = "unknown",
            address = " address ",
            displayName = "bad\u0000name",
            exportOnlyReason = " ",
            canExportSecrets = false,
            canSignTransactions = true,
            discoveredAtMillis = 10,
            lastExportedAtMillis = 9
        )
        val snapshot = snapshot(
            schemaVersion = 99,
            cutoffAtMillis = 0,
            evaluatedAtMillis = 0,
            legacyVaults = listOf(badVault, badVault)
        )

        val errors = snapshot.validationErrors()

        assertTrue(errors.contains(UniversalWalletMigrationValidationError.InvalidSchemaVersion))
        assertTrue(errors.contains(UniversalWalletMigrationValidationError.InvalidTimestamp))
        assertTrue(errors.contains(UniversalWalletMigrationValidationError.InvalidVaultId))
        assertTrue(errors.contains(UniversalWalletMigrationValidationError.DuplicateVaultId))
        assertTrue(errors.contains(UniversalWalletMigrationValidationError.InvalidAccountId))
        assertTrue(errors.contains(UniversalWalletMigrationValidationError.InvalidEcosystem))
        assertTrue(errors.contains(UniversalWalletMigrationValidationError.InvalidAddress))
        assertTrue(errors.contains(UniversalWalletMigrationValidationError.InvalidDisplayName))
        assertTrue(errors.contains(UniversalWalletMigrationValidationError.InvalidExportReason))
        assertTrue(errors.contains(UniversalWalletMigrationValidationError.ExportDisabled))
        assertTrue(errors.contains(UniversalWalletMigrationValidationError.LegacySigningEnabled))
    }

    private fun snapshot(
        schemaVersion: Int = UniversalWalletMigrationSnapshot.SCHEMA_VERSION,
        hasUniversalWallet: Boolean = false,
        legacyVaults: List<UniversalWalletLegacyVaultDescriptor> = listOf(legacyVault()),
        cutoffAtMillis: Long = 1_710_000_000_000,
        evaluatedAtMillis: Long = 1_710_000_000_100
    ) = UniversalWalletMigrationSnapshot(
        schemaVersion = schemaVersion,
        platform = UniversalWalletMigrationPlatform.Android,
        hasUniversalWallet = hasUniversalWallet,
        legacyVaults = legacyVaults,
        cutoffAtMillis = cutoffAtMillis,
        evaluatedAtMillis = evaluatedAtMillis
    )

    private fun legacyVault() = UniversalWalletLegacyVaultDescriptor(
        vaultId = "legacy_12345678",
        accountId = "substrate-legacy",
        ecosystem = UniversalWalletEcosystem.Substrate,
        address = "15FKRtF6nX3SE4PaW4LX69XqYHq2oSep9JX5KF4cgN1XkZ4q",
        displayName = "Legacy DOT",
        exportOnlyReason = "pre-cutoff account export",
        discoveredAtMillis = 1_700_000_000_000,
        lastExportedAtMillis = 1_700_000_000_100
    )
}
