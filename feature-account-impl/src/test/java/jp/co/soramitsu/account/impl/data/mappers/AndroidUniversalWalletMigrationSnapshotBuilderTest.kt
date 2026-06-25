package jp.co.soramitsu.account.impl.data.mappers

import jp.co.soramitsu.account.api.domain.model.AndroidUniversalWalletMigrationSnapshotBuilder
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.common.model.UniversalWalletEcosystem
import jp.co.soramitsu.common.model.UniversalWalletMigrationRequiredAction
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.core.models.CryptoType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUniversalWalletMigrationSnapshotBuilderTest {

    private val builder = AndroidUniversalWalletMigrationSnapshotBuilder(
        cutoffAtMillis = CUTOFF_AT,
        clockMillis = { EVALUATED_AT }
    )

    @Test
    fun `fresh install requires universal wallet creation`() {
        val snapshot = builder.build(emptyList())

        assertFalse(snapshot.hasUniversalWallet)
        assertTrue(snapshot.legacyVaults.isEmpty())
        assertEquals(UniversalWalletMigrationRequiredAction.CreateUniversalWallet, snapshot.requiredAction())
        assertTrue(snapshot.validationErrors().isEmpty())
    }

    @Test
    fun `builder clamps evaluated time to cutoff`() {
        val snapshot = AndroidUniversalWalletMigrationSnapshotBuilder(
            cutoffAtMillis = CUTOFF_AT,
            clockMillis = { 1L }
        ).build(emptyList())

        assertEquals(CUTOFF_AT, snapshot.evaluatedAtMillis)
        assertTrue(snapshot.validationErrors().isEmpty())
    }

    @Test
    fun `legacy root accounts become export-only vault descriptors`() {
        val snapshot = builder.build(
            listOf(
                lightAccount(
                    substrateAccountId = ByteArray(32) { 1 },
                    ethereumAddress = ByteArray(20) { 2 },
                    tonPublicKey = ByteArray(32) { 3 }
                )
            )
        )

        assertFalse(snapshot.hasUniversalWallet)
        assertEquals(UniversalWalletMigrationRequiredAction.MigrateBeforeAccess, snapshot.requiredAction())
        assertEquals(
            listOf(
                UniversalWalletEcosystem.Substrate.id,
                UniversalWalletEcosystem.Evm.id,
                UniversalWalletEcosystem.Ton.id
            ),
            snapshot.legacyVaults.map { it.ecosystem }
        )
        assertTrue(snapshot.legacyVaults.all { it.canExportSecrets })
        assertTrue(snapshot.legacyVaults.none { it.canSignTransactions })
        assertEquals(snapshot.legacyVaults.size, snapshot.legacyVaults.map { it.vaultId }.toSet().size)
        assertTrue(snapshot.validationErrors().isEmpty())
    }

    @Test
    fun `complete universal wallet allows normal access without legacy descriptors`() {
        val bitcoin = BitcoinKeyDerivation.deriveAccount(
            mnemonic = MNEMONIC,
            network = BitcoinKeyDerivation.Network.Mainnet
        )
        val solana = SolanaKeyDerivation.deriveAccount(MNEMONIC)
        val iroha = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val snapshot = builder.build(
            listOf(
                lightAccount(
                    substrateAccountId = ByteArray(32) { 1 },
                    ethereumAddress = ByteArray(20) { 2 },
                    tonPublicKey = ByteArray(32) { 3 },
                    universalWalletChainAccounts = mapOf(
                        UniversalWalletRegistry.bitcoinMainnet.chainId to universalWalletAccount(bitcoin.publicKey),
                        UniversalWalletRegistry.solanaMainnet.chainId to universalWalletAccount(solana.publicKey),
                        UniversalWalletRegistry.taira.chainId to universalWalletAccount(iroha.publicKey)
                    )
                )
            )
        )

        assertTrue(snapshot.hasUniversalWallet)
        assertTrue(snapshot.legacyVaults.isEmpty())
        assertEquals(UniversalWalletMigrationRequiredAction.NormalAccess, snapshot.requiredAction())
        assertTrue(snapshot.validationErrors().isEmpty())
    }

    @Test
    fun `partial universal wallet material does not unlock normal access`() {
        val solana = SolanaKeyDerivation.deriveAccount(MNEMONIC)
        val snapshot = builder.build(
            listOf(
                lightAccount(
                    universalWalletChainAccounts = mapOf(
                        UniversalWalletRegistry.solanaMainnet.chainId to universalWalletAccount(solana.publicKey)
                    )
                )
            )
        )

        assertFalse(snapshot.hasUniversalWallet)
        assertTrue(snapshot.legacyVaults.isEmpty())
        assertEquals(UniversalWalletMigrationRequiredAction.CreateUniversalWallet, snapshot.requiredAction())
        assertTrue(snapshot.validationErrors().isEmpty())
    }

    @Test
    fun `partial universal wallet material preserves legacy export-only descriptors`() {
        val solana = SolanaKeyDerivation.deriveAccount(MNEMONIC)
        val snapshot = builder.build(
            listOf(
                lightAccount(
                    substrateAccountId = ByteArray(32) { 1 },
                    universalWalletChainAccounts = mapOf(
                        UniversalWalletRegistry.solanaMainnet.chainId to universalWalletAccount(solana.publicKey)
                    )
                )
            )
        )

        assertFalse(snapshot.hasUniversalWallet)
        assertEquals(UniversalWalletMigrationRequiredAction.MigrateBeforeAccess, snapshot.requiredAction())
        assertEquals(listOf(UniversalWalletEcosystem.Substrate.id), snapshot.legacyVaults.map { it.ecosystem })
        assertTrue(snapshot.validationErrors().isEmpty())
    }

    @Test
    fun `malformed legacy public data still produces a valid fail-closed descriptor`() {
        val snapshot = builder.build(
            listOf(
                lightAccount(
                    substrateAccountId = byteArrayOf(1),
                    name = "  Bad\u0000Name That Is Far Too Long For The Shared Human Text Contract And Must Be Trimmed  "
                )
            )
        )
        val descriptor = snapshot.legacyVaults.single()

        assertEquals(UniversalWalletMigrationRequiredAction.MigrateBeforeAccess, snapshot.requiredAction())
        assertEquals("unavailable:android:1:substrate", descriptor.address)
        assertFalse(descriptor.displayName.orEmpty().contains('\u0000'))
        assertTrue(descriptor.displayName.orEmpty().length <= 64)
        assertTrue(snapshot.validationErrors().isEmpty())
    }

    private fun universalWalletAccount(publicKey: ByteArray): LightMetaAccount.UniversalWalletChainAccount {
        return LightMetaAccount.UniversalWalletChainAccount(
            publicKey = publicKey,
            accountId = publicKey,
            cryptoType = CryptoType.ED25519
        )
    }

    private fun lightAccount(
        substrateAccountId: ByteArray? = null,
        ethereumAddress: ByteArray? = null,
        tonPublicKey: ByteArray? = null,
        universalWalletChainAccounts: Map<String, LightMetaAccount.UniversalWalletChainAccount> = emptyMap(),
        name: String = "Wallet"
    ): LightMetaAccount {
        return LightMetaAccount(
            id = 1,
            substratePublicKey = substrateAccountId,
            substrateCryptoType = substrateAccountId?.let { CryptoType.ED25519 },
            substrateAccountId = substrateAccountId,
            ethereumAddress = ethereumAddress,
            ethereumPublicKey = ethereumAddress,
            tonPublicKey = tonPublicKey,
            universalWalletChainAccounts = universalWalletChainAccounts,
            isSelected = true,
            name = name,
            isBackedUp = true,
            initialized = true
        )
    }

    private companion object {
        const val CUTOFF_AT = 1_710_000_000_000L
        const val EVALUATED_AT = 1_710_000_000_100L
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    }
}
