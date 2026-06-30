package jp.co.soramitsu.account.api.domain.model

import jp.co.soramitsu.common.model.UniversalWalletEcosystem
import jp.co.soramitsu.common.model.UniversalWalletLegacyVaultDescriptor
import jp.co.soramitsu.common.model.UniversalWalletLegacyVaultMode
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.moonriverChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonChainId

data class AndroidUniversalWalletLegacyExportTarget(
    val metaId: Long,
    val chainId: ChainId,
    val ecosystem: UniversalWalletEcosystem,
    val descriptor: UniversalWalletLegacyVaultDescriptor
)

object AndroidUniversalWalletLegacyExportResolver {

    fun resolve(
        descriptor: UniversalWalletLegacyVaultDescriptor,
        accounts: List<LightMetaAccount>
    ): AndroidUniversalWalletLegacyExportTarget? {
        if (!descriptor.canExportSecrets || descriptor.canSignTransactions) {
            return null
        }

        if (descriptor.mode != UniversalWalletLegacyVaultMode.ExportOnly || descriptor.validationErrors().isNotEmpty()) {
            return null
        }

        val ecosystem = UniversalWalletEcosystem.fromId(descriptor.ecosystem) ?: return null
        val chainId = legacyExportChainId(ecosystem) ?: return null

        val account = accounts.firstOrNull {
            descriptor.vaultId == expectedVaultId(it.id, ecosystem) &&
                descriptor.accountId == expectedAccountId(it.id, ecosystem) &&
                it.hasLegacyRootMaterial(ecosystem)
        } ?: return null

        return AndroidUniversalWalletLegacyExportTarget(
            metaId = account.id,
            chainId = chainId,
            ecosystem = ecosystem,
            descriptor = descriptor
        )
    }

    private fun legacyExportChainId(ecosystem: UniversalWalletEcosystem): ChainId? {
        return when (ecosystem) {
            UniversalWalletEcosystem.Substrate -> polkadotChainId
            UniversalWalletEcosystem.Evm -> moonriverChainId
            UniversalWalletEcosystem.Ton -> tonChainId
            UniversalWalletEcosystem.Bitcoin,
            UniversalWalletEcosystem.Solana,
            UniversalWalletEcosystem.Iroha -> null
        }
    }

    private fun LightMetaAccount.hasLegacyRootMaterial(ecosystem: UniversalWalletEcosystem): Boolean {
        return when (ecosystem) {
            UniversalWalletEcosystem.Substrate -> substrateAccountId != null
            UniversalWalletEcosystem.Evm -> ethereumAddress != null || ethereumPublicKey != null
            UniversalWalletEcosystem.Ton -> tonPublicKey != null
            UniversalWalletEcosystem.Bitcoin,
            UniversalWalletEcosystem.Solana,
            UniversalWalletEcosystem.Iroha -> false
        }
    }

    private fun expectedVaultId(id: Long, ecosystem: UniversalWalletEcosystem): String {
        return "legacy_android_${id}_${ecosystem.id}"
    }

    private fun expectedAccountId(id: Long, ecosystem: UniversalWalletEcosystem): String {
        return "android-${id}-${ecosystem.id}"
    }
}
