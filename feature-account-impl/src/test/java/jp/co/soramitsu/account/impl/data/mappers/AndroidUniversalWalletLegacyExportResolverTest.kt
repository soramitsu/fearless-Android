package jp.co.soramitsu.account.impl.data.mappers

import jp.co.soramitsu.account.api.domain.model.AndroidUniversalWalletLegacyExportResolver
import jp.co.soramitsu.account.api.domain.model.AndroidUniversalWalletMigrationSnapshotBuilder
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.common.model.UniversalWalletEcosystem
import jp.co.soramitsu.common.model.UniversalWalletLegacyVaultDescriptor
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.moonriverChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonChainId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidUniversalWalletLegacyExportResolverTest {

    private val builder = AndroidUniversalWalletMigrationSnapshotBuilder(
        cutoffAtMillis = CUTOFF_AT,
        clockMillis = { EVALUATED_AT }
    )

    @Test
    fun `resolves legacy root descriptors to existing export chain targets`() {
        val account = lightAccount(
            id = 42,
            substrateAccountId = ByteArray(32) { 1 },
            ethereumAddress = ByteArray(20) { 2 },
            tonPublicKey = ByteArray(32) { 3 }
        )
        val descriptors = builder.build(listOf(account)).legacyVaults.associateBy { it.ecosystem }

        val substrate = AndroidUniversalWalletLegacyExportResolver.resolve(
            descriptors.getValue(UniversalWalletEcosystem.Substrate.id),
            listOf(account)
        )
        val evm = AndroidUniversalWalletLegacyExportResolver.resolve(
            descriptors.getValue(UniversalWalletEcosystem.Evm.id),
            listOf(account)
        )
        val ton = AndroidUniversalWalletLegacyExportResolver.resolve(
            descriptors.getValue(UniversalWalletEcosystem.Ton.id),
            listOf(account)
        )

        assertEquals(42L, substrate?.metaId)
        assertEquals(polkadotChainId, substrate?.chainId)
        assertEquals(UniversalWalletEcosystem.Substrate, substrate?.ecosystem)
        assertEquals(moonriverChainId, evm?.chainId)
        assertEquals(UniversalWalletEcosystem.Evm, evm?.ecosystem)
        assertEquals(tonChainId, ton?.chainId)
        assertEquals(UniversalWalletEcosystem.Ton, ton?.ecosystem)
    }

    @Test
    fun `rejects descriptors that are not export-only safe`() {
        val account = lightAccount(id = 42, substrateAccountId = ByteArray(32) { 1 })
        val descriptor = builder.build(listOf(account)).legacyVaults.single()

        assertNull(
            AndroidUniversalWalletLegacyExportResolver.resolve(
                descriptor.copy(canSignTransactions = true),
                listOf(account)
            )
        )
        assertNull(
            AndroidUniversalWalletLegacyExportResolver.resolve(
                descriptor.copy(canExportSecrets = false),
                listOf(account)
            )
        )
    }

    @Test
    fun `rejects descriptors with tampered deterministic account ids`() {
        val account = lightAccount(id = 42, substrateAccountId = ByteArray(32) { 1 })
        val descriptor = builder.build(listOf(account)).legacyVaults.single()

        assertNull(
            AndroidUniversalWalletLegacyExportResolver.resolve(
                descriptor.copy(vaultId = "legacy_android_99_substrate"),
                listOf(account)
            )
        )
        assertNull(
            AndroidUniversalWalletLegacyExportResolver.resolve(
                descriptor.copy(accountId = "android-99-substrate"),
                listOf(account)
            )
        )
    }

    @Test
    fun `rejects unsupported universal wallet ecosystems for legacy export`() {
        val account = lightAccount(id = 42, substrateAccountId = ByteArray(32) { 1 })
        val descriptor = legacyDescriptor(UniversalWalletEcosystem.Bitcoin)

        assertNull(AndroidUniversalWalletLegacyExportResolver.resolve(descriptor, listOf(account)))
    }

    @Test
    fun `rejects descriptor when matching account lacks root material`() {
        val account = lightAccount(id = 42, substrateAccountId = ByteArray(32) { 1 })
        val descriptor = builder.build(listOf(account)).legacyVaults.single()
        val accountWithoutRoot = lightAccount(id = 42)

        assertNull(AndroidUniversalWalletLegacyExportResolver.resolve(descriptor, listOf(accountWithoutRoot)))
    }

    private fun legacyDescriptor(ecosystem: UniversalWalletEcosystem): UniversalWalletLegacyVaultDescriptor {
        return UniversalWalletLegacyVaultDescriptor(
            vaultId = "legacy_android_42_${ecosystem.id}",
            accountId = "android-42-${ecosystem.id}",
            ecosystem = ecosystem,
            address = "unavailable:android:42:${ecosystem.id}",
            displayName = "Wallet",
            exportOnlyReason = "pre-cutoff account export",
            canExportSecrets = true,
            canSignTransactions = false,
            discoveredAtMillis = CUTOFF_AT
        )
    }

    private fun lightAccount(
        id: Long,
        substrateAccountId: ByteArray? = null,
        ethereumAddress: ByteArray? = null,
        tonPublicKey: ByteArray? = null
    ): LightMetaAccount {
        return LightMetaAccount(
            id = id,
            substratePublicKey = substrateAccountId,
            substrateCryptoType = substrateAccountId?.let { CryptoType.ED25519 },
            substrateAccountId = substrateAccountId,
            ethereumAddress = ethereumAddress,
            ethereumPublicKey = ethereumAddress,
            tonPublicKey = tonPublicKey,
            universalWalletChainAccounts = emptyMap(),
            isSelected = true,
            name = "Wallet",
            isBackedUp = true,
            initialized = true
        )
    }

    private companion object {
        const val CUTOFF_AT = 1_710_000_000_000L
        const val EVALUATED_AT = 1_710_000_000_100L
    }
}
