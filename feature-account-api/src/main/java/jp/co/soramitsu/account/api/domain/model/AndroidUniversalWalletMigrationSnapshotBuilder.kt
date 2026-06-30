package jp.co.soramitsu.account.api.domain.model

import jp.co.soramitsu.common.model.UniversalWalletEcosystem
import jp.co.soramitsu.common.model.UniversalWalletLegacyVaultDescriptor
import jp.co.soramitsu.common.model.UniversalWalletMigrationPlatform
import jp.co.soramitsu.common.model.UniversalWalletMigrationSnapshot
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.common.utils.IrohaAddressCodec
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.ethereumAddressToHex
import jp.co.soramitsu.common.utils.v4r2tonAddress
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class AndroidUniversalWalletMigrationSnapshotBuilder(
    private val cutoffAtMillis: Long,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {

    fun build(accounts: List<LightMetaAccount>): UniversalWalletMigrationSnapshot {
        return UniversalWalletMigrationSnapshot(
            platform = UniversalWalletMigrationPlatform.Android,
            hasUniversalWallet = accounts.any { it.hasCompleteUniversalWallet() },
            legacyVaults = accounts.flatMap { account ->
                if (account.hasCompleteUniversalWallet()) {
                    emptyList()
                } else {
                    account.legacyVaultDescriptors()
                }
            },
            cutoffAtMillis = cutoffAtMillis,
            evaluatedAtMillis = maxOf(clockMillis(), cutoffAtMillis)
        )
    }

    private fun LightMetaAccount.legacyVaultDescriptors(): List<UniversalWalletLegacyVaultDescriptor> {
        return listOfNotNull(
            substrateAccountId?.let {
                legacyVault(
                    account = this,
                    ecosystem = UniversalWalletEcosystem.Substrate,
                    address = substrateAddressOrFallback()
                )
            },
            (ethereumAddress ?: ethereumPublicKey)?.let {
                legacyVault(
                    account = this,
                    ecosystem = UniversalWalletEcosystem.Evm,
                    address = evmAddressOrFallback()
                )
            },
            tonPublicKey?.let {
                legacyVault(
                    account = this,
                    ecosystem = UniversalWalletEcosystem.Ton,
                    address = tonAddressOrFallback()
                )
            }
        )
    }

    private fun legacyVault(
        account: LightMetaAccount,
        ecosystem: UniversalWalletEcosystem,
        address: String
    ): UniversalWalletLegacyVaultDescriptor {
        return UniversalWalletLegacyVaultDescriptor(
            vaultId = "legacy_android_${account.id}_${ecosystem.id}",
            accountId = "android-${account.id}-${ecosystem.id}",
            ecosystem = ecosystem,
            address = address,
            displayName = account.name.safeDisplayName(),
            exportOnlyReason = "pre-cutoff account export",
            canExportSecrets = true,
            canSignTransactions = false,
            discoveredAtMillis = cutoffAtMillis
        )
    }

    private fun LightMetaAccount.hasCompleteUniversalWallet(): Boolean {
        return substrateAddressOrNull() != null &&
            evmAddressOrNull() != null &&
            tonAddressOrNull() != null &&
            bitcoinAddressOrNull() != null &&
            solanaAddressOrNull() != null &&
            irohaAddressOrNull() != null
    }

    private fun LightMetaAccount.substrateAddressOrFallback(): String {
        return substrateAddressOrNull() ?: unavailableAddress(UniversalWalletEcosystem.Substrate)
    }

    private fun LightMetaAccount.substrateAddressOrNull(): String? {
        return runCatching {
            substrateAccountId
                ?.takeIf { it.size == SUBSTRATE_ACCOUNT_ID_BYTES }
                ?.toAddress(0.toShort())
        }.getOrNull()
    }

    private fun LightMetaAccount.evmAddressOrFallback(): String {
        return evmAddressOrNull() ?: unavailableAddress(UniversalWalletEcosystem.Evm)
    }

    private fun LightMetaAccount.evmAddressOrNull(): String? {
        return runCatching {
            ethereumAddress
                ?.takeIf { it.size == EVM_ADDRESS_BYTES }
                ?.ethereumAddressToHex()
        }.getOrNull()
    }

    private fun LightMetaAccount.tonAddressOrFallback(): String {
        return tonAddressOrNull() ?: unavailableAddress(UniversalWalletEcosystem.Ton)
    }

    private fun LightMetaAccount.tonAddressOrNull(): String? {
        return runCatching {
            tonPublicKey
                ?.takeIf { it.size == TON_PUBLIC_KEY_BYTES }
                ?.v4r2tonAddress(isTestnet = false)
        }.getOrNull()
    }

    private fun LightMetaAccount.bitcoinAddressOrNull(): String? {
        return universalWalletChainAccounts.firstPublicKey(BITCOIN_MAINNET_CHAIN_IDS)?.let {
            runCatching {
                BitcoinKeyDerivation.addressFromPublicKey(it, BitcoinKeyDerivation.Network.Mainnet)
            }.getOrNull()
        } ?: universalWalletChainAccounts.firstPublicKey(BITCOIN_TESTNET_CHAIN_IDS)?.let {
            runCatching {
                BitcoinKeyDerivation.addressFromPublicKey(it, BitcoinKeyDerivation.Network.Testnet)
            }.getOrNull()
        }
    }

    private fun LightMetaAccount.solanaAddressOrNull(): String? {
        return universalWalletChainAccounts.firstPublicKey(SOLANA_CHAIN_IDS)?.let {
            runCatching {
                SolanaKeyDerivation.addressFromPublicKey(it)
            }.getOrNull()
        }
    }

    private fun LightMetaAccount.irohaAddressOrNull(): String? {
        return universalWalletChainAccounts.firstPublicKey(TAIRA_CHAIN_IDS)?.let {
            runCatching {
                IrohaAddressCodec.encode(
                    publicKeyHex = it.toHexString(withPrefix = false),
                    chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
                )
            }.getOrNull()
        } ?: universalWalletChainAccounts.firstPublicKey(NEXUS_CHAIN_IDS)?.let {
            runCatching {
                IrohaAddressCodec.encode(
                    publicKeyHex = it.toHexString(withPrefix = false),
                    chainDiscriminant = UniversalWalletRegistry.nexus.chainDiscriminant
                )
            }.getOrNull()
        }
    }

    private fun LightMetaAccount.unavailableAddress(ecosystem: UniversalWalletEcosystem): String {
        return "unavailable:android:$id:${ecosystem.id}"
    }

    private fun Map<ChainId, LightMetaAccount.UniversalWalletChainAccount>.firstPublicKey(
        chainIds: Set<ChainId>
    ): ByteArray? {
        return chainIds.firstNotNullOfOrNull { get(it)?.publicKey }
    }

    private fun String.safeDisplayName(): String? {
        val sanitized = trim()
            .filterNot { it.isISOControl() }
            .take(64)

        return sanitized.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val SUBSTRATE_ACCOUNT_ID_BYTES = 32
        private const val EVM_ADDRESS_BYTES = 20
        private const val TON_PUBLIC_KEY_BYTES = 32

        private val BITCOIN_MAINNET_CHAIN_IDS = setOf(
            UniversalWalletRegistry.bitcoinMainnet.id,
            UniversalWalletRegistry.bitcoinMainnet.chainId
        )

        private val BITCOIN_TESTNET_CHAIN_IDS = setOf(
            UniversalWalletRegistry.bitcoinTestnet.id,
            UniversalWalletRegistry.bitcoinTestnet.chainId
        )

        private val SOLANA_CHAIN_IDS = setOf(
            UniversalWalletRegistry.solanaMainnet.id,
            UniversalWalletRegistry.solanaMainnet.chainId,
            UniversalWalletRegistry.solanaDevnet.id,
            UniversalWalletRegistry.solanaDevnet.chainId
        )

        private val TAIRA_CHAIN_IDS = setOf(
            UniversalWalletRegistry.taira.id,
            UniversalWalletRegistry.taira.chainId
        )

        private val NEXUS_CHAIN_IDS = setOf(
            UniversalWalletRegistry.nexus.id,
            UniversalWalletRegistry.nexus.chainId
        )
    }
}
