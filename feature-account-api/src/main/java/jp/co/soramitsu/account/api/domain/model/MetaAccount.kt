package jp.co.soramitsu.account.api.domain.model

import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.common.utils.IrohaAddressCodec
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.ethereumAddressToHex
import jp.co.soramitsu.common.utils.v4r2tonAddress
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.ext.bitcoinAddressFromPublicKey
import jp.co.soramitsu.runtime.ext.irohaAddressFromPublicKey
import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.isUniversalWalletIroha
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import jp.co.soramitsu.runtime.ext.solanaAddressFromPublicKey
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress

class MetaAccountOrdering(
    val id: Long,
    val position: Int
)

interface LightMetaAccount {
    val id: Long
    val substratePublicKey: ByteArray?
    val substrateCryptoType: CryptoType?
    val substrateAccountId: ByteArray?
    val ethereumAddress: ByteArray?
    val ethereumPublicKey: ByteArray?
    val tonPublicKey: ByteArray?
    val universalWalletChainAccounts: Map<ChainId, UniversalWalletChainAccount>
    val isSelected: Boolean
    val name: String
    val isBackedUp: Boolean
    val initialized: Boolean

    class UniversalWalletChainAccount(
        val publicKey: ByteArray,
        val accountId: ByteArray,
        val cryptoType: CryptoType
    )
}

fun LightMetaAccount(
    id: Long,
    substratePublicKey: ByteArray?,
    substrateCryptoType: CryptoType?,
    substrateAccountId: ByteArray?,
    ethereumAddress: ByteArray?,
    ethereumPublicKey: ByteArray?,
    tonPublicKey: ByteArray?,
    universalWalletChainAccounts: Map<ChainId, LightMetaAccount.UniversalWalletChainAccount> = emptyMap(),
    isSelected: Boolean,
    name: String,
    isBackedUp: Boolean,
    initialized: Boolean
) = object : LightMetaAccount {
    override val id: Long = id
    override val substratePublicKey: ByteArray? = substratePublicKey
    override val substrateCryptoType: CryptoType? = substrateCryptoType
    override val substrateAccountId: ByteArray? = substrateAccountId
    override val ethereumAddress: ByteArray? = ethereumAddress
    override val ethereumPublicKey: ByteArray? = ethereumPublicKey
    override val tonPublicKey: ByteArray? = tonPublicKey
    override val universalWalletChainAccounts = universalWalletChainAccounts
    override val isSelected: Boolean = isSelected
    override val name: String = name
    override val isBackedUp: Boolean = isBackedUp
    override val initialized: Boolean = initialized
}

data class MetaAccount(
    override val id: Long,
    val chainAccounts: Map<ChainId, ChainAccount>,
    val favoriteChains: Map<ChainId, FavoriteChain>,
    override val substratePublicKey: ByteArray?,
    override val substrateCryptoType: CryptoType?,
    override val substrateAccountId: ByteArray?,
    override val ethereumAddress: ByteArray?,
    override val ethereumPublicKey: ByteArray?,
    override val tonPublicKey: ByteArray?,
    override val isSelected: Boolean,
    override val isBackedUp: Boolean,
    val googleBackupAddress: String?,
    override val name: String,
    override val initialized: Boolean
) : LightMetaAccount {

    class ChainAccount(
        val metaId: Long,
        val chain: Chain?,
        val publicKey: ByteArray,
        val accountId: ByteArray,
        val cryptoType: CryptoType,
        val accountName: String
    )

    class FavoriteChain(
        val chain: Chain?,
        val isFavorite: Boolean
    )

    override val universalWalletChainAccounts: Map<ChainId, LightMetaAccount.UniversalWalletChainAccount>
        get() = chainAccounts.mapValues { (_, account) ->
            LightMetaAccount.UniversalWalletChainAccount(
                publicKey = account.publicKey,
                accountId = account.accountId,
                cryptoType = account.cryptoType
            )
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MetaAccount

        if (id != other.id) return false
        if (chainAccounts != other.chainAccounts) return false
        if (favoriteChains != other.favoriteChains) return false
        if (!substratePublicKey.contentEquals(other.substratePublicKey)) return false
        if (substrateCryptoType != other.substrateCryptoType) return false
        if (!substrateAccountId.contentEquals(other.substrateAccountId)) return false
        if (ethereumAddress != null) {
            if (other.ethereumAddress == null) return false
            if (!ethereumAddress.contentEquals(other.ethereumAddress)) return false
        } else if (other.ethereumAddress != null) return false
        if (ethereumPublicKey != null) {
            if (other.ethereumPublicKey == null) return false
            if (!ethereumPublicKey.contentEquals(other.ethereumPublicKey)) return false
        } else if (other.ethereumPublicKey != null) return false
        if (isSelected != other.isSelected) return false
        if (name != other.name) return false
        if (initialized != other.initialized) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + chainAccounts.hashCode()
        result = 31 * result + favoriteChains.hashCode()
        result = 31 * result + substratePublicKey.contentHashCode()
        result = 31 * result + substrateCryptoType.hashCode()
        result = 31 * result + substrateAccountId.contentHashCode()
        result = 31 * result + (ethereumAddress?.contentHashCode() ?: 0)
        result = 31 * result + (ethereumPublicKey?.contentHashCode() ?: 0)
        result = 31 * result + isSelected.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + initialized.hashCode()
        return result
    }
}

fun MetaAccount.hasChainAccount(chainId: ChainId) = chainId in chainAccounts

fun MetaAccount.cryptoType(chain: IChain): CryptoType? {
    return when {
        chain is Chain && chain.isUniversalWalletChain() -> chainAccounts.universalWalletChainAccount(chain)?.cryptoType
        hasChainAccount(chain.id) -> chainAccounts.getValue(chain.id).cryptoType
        chain.isEthereumBased -> CryptoType.ECDSA
        else -> substrateCryptoType
    }
}

fun MetaAccount.address(chain: Chain): String? {
    return kotlin.runCatching {
        when {
            chain.isUniversalWalletBitcoin() -> chainAccounts.universalWalletChainAccount(chain)?.publicKey?.let(chain::bitcoinAddressFromPublicKey)
            chain.isUniversalWalletSolana() -> chainAccounts.universalWalletChainAccount(chain)?.publicKey?.let(chain::solanaAddressFromPublicKey)
            chain.isUniversalWalletIroha() -> chainAccounts.universalWalletChainAccount(chain)?.publicKey?.let(chain::irohaAddressFromPublicKey)
            hasChainAccount(chain.id) -> chain.addressOf(chainAccounts.getValue(chain.id).accountId)
            chain.ecosystem == Ecosystem.EthereumBased || chain.ecosystem == Ecosystem.Ethereum -> ethereumAddress?.ethereumAddressToHex()
            chain.ecosystem == Ecosystem.Ton -> {
                tonPublicKey?.v4r2tonAddress(chain.isTestNet)
            }
            chain.ecosystem == Ecosystem.Substrate -> substrateAccountId?.toAddress(chain.addressPrefix.toShort())
            else -> null
        }
    }.getOrNull()
}

fun LightMetaAccount.address(chain: Chain): String? {
    return kotlin.runCatching {
        when {
            chain.isUniversalWalletBitcoin() -> universalWalletChainAccounts.universalWalletChainAccount(chain)?.publicKey?.let(chain::bitcoinAddressFromPublicKey)
            chain.isUniversalWalletSolana() -> universalWalletChainAccounts.universalWalletChainAccount(chain)?.publicKey?.let(chain::solanaAddressFromPublicKey)
            chain.isUniversalWalletIroha() -> universalWalletChainAccounts.universalWalletChainAccount(chain)?.publicKey?.let(chain::irohaAddressFromPublicKey)
            chain.ecosystem == Ecosystem.Substrate -> substrateAccountId?.toAddress(chain.addressPrefix.toShort())
            chain.ecosystem == Ecosystem.EthereumBased || chain.ecosystem == Ecosystem.Ethereum -> ethereumAddress?.ethereumAddressToHex()
            chain.ecosystem == Ecosystem.Ton -> {
                tonPublicKey?.v4r2tonAddress(chain.isTestNet)
            }
            else -> null
        }
    }.getOrNull()
}

fun LightMetaAccount.supportedEcosystemWithIconAddress(): Map<WalletEcosystem, String> {
    return listOfNotNull(
        tonPublicKey?.let { WalletEcosystem.Ton to it.v4r2tonAddress(false) },
        substratePublicKey?.let { WalletEcosystem.Substrate to it.toAddress(0.toShort()) }, // 0 = polkadotAddressPrefix
        ethereumPublicKey?.let { WalletEcosystem.Ethereum to it.ethereumAddressToHex() },
        universalWalletChainAccounts.bitcoinIconAddress()?.let { WalletEcosystem.Bitcoin to it },
        universalWalletChainAccounts.solanaIconAddress()?.let { WalletEcosystem.Solana to it },
        universalWalletChainAccounts.irohaIconAddress()?.let { WalletEcosystem.Iroha to it }
    ).toMap()
}

fun LightMetaAccount.supportedEcosystems(): Set<WalletEcosystem> = setOfNotNull(
    tonPublicKey?.let { WalletEcosystem.Ton },
    substratePublicKey?.let { WalletEcosystem.Substrate },
    ethereumPublicKey?.let { WalletEcosystem.Ethereum },
    universalWalletChainAccounts.takeIf { it.hasAnyChainId(BITCOIN_CHAIN_IDS) }?.let { WalletEcosystem.Bitcoin },
    universalWalletChainAccounts.takeIf { it.hasAnyChainId(SOLANA_CHAIN_IDS) }?.let { WalletEcosystem.Solana },
    universalWalletChainAccounts.takeIf { it.hasAnyChainId(IROHA_CHAIN_IDS) }?.let { WalletEcosystem.Iroha }
)

private fun Map<ChainId, LightMetaAccount.UniversalWalletChainAccount>.bitcoinIconAddress(): String? {
    val mainnet = firstAccount(BITCOIN_MAINNET_CHAIN_IDS)?.publicKey?.let {
        runCatching {
            BitcoinKeyDerivation.addressFromPublicKey(it, BitcoinKeyDerivation.Network.Mainnet)
        }.getOrNull()
    }

    return mainnet ?: firstAccount(BITCOIN_TESTNET_CHAIN_IDS)?.publicKey?.let {
        runCatching {
            BitcoinKeyDerivation.addressFromPublicKey(it, BitcoinKeyDerivation.Network.Testnet)
        }.getOrNull()
    }
}

private fun Map<ChainId, LightMetaAccount.UniversalWalletChainAccount>.solanaIconAddress(): String? {
    return firstAccount(SOLANA_CHAIN_IDS)?.publicKey?.let {
        runCatching {
            SolanaKeyDerivation.addressFromPublicKey(it)
        }.getOrNull()
    }
}

private fun Map<ChainId, LightMetaAccount.UniversalWalletChainAccount>.irohaIconAddress(): String? {
    val taira = firstAccount(TAIRA_CHAIN_IDS)?.publicKey?.let {
        runCatching {
            IrohaAddressCodec.encode(
                publicKeyHex = it.toHexString(withPrefix = false),
                chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
            )
        }.getOrNull()
    }

    return taira ?: firstAccount(NEXUS_CHAIN_IDS)?.publicKey?.let {
        runCatching {
            IrohaAddressCodec.encode(
                publicKeyHex = it.toHexString(withPrefix = false),
                chainDiscriminant = UniversalWalletRegistry.nexus.chainDiscriminant
            )
        }.getOrNull()
    }
}

private fun Map<ChainId, LightMetaAccount.UniversalWalletChainAccount>.firstAccount(
    chainIds: Set<ChainId>
): LightMetaAccount.UniversalWalletChainAccount? {
    return chainIds.firstNotNullOfOrNull { get(it) }
}

private fun Map<ChainId, LightMetaAccount.UniversalWalletChainAccount>.hasAnyChainId(
    chainIds: Set<ChainId>
): Boolean {
    return keys.any(chainIds::contains)
}

private val BITCOIN_MAINNET_CHAIN_IDS = setOf(
    UniversalWalletRegistry.bitcoinMainnet.id,
    UniversalWalletRegistry.bitcoinMainnet.chainId
)

private val BITCOIN_TESTNET_CHAIN_IDS = setOf(
    UniversalWalletRegistry.bitcoinTestnet.id,
    UniversalWalletRegistry.bitcoinTestnet.chainId
)

private val SOLANA_MAINNET_CHAIN_IDS = setOf(
    UniversalWalletRegistry.solanaMainnet.id,
    UniversalWalletRegistry.solanaMainnet.chainId
)

private val SOLANA_DEVNET_CHAIN_IDS = setOf(
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

private val BITCOIN_CHAIN_IDS = BITCOIN_MAINNET_CHAIN_IDS + BITCOIN_TESTNET_CHAIN_IDS
private val SOLANA_CHAIN_IDS = SOLANA_MAINNET_CHAIN_IDS + SOLANA_DEVNET_CHAIN_IDS
private val IROHA_CHAIN_IDS = TAIRA_CHAIN_IDS + NEXUS_CHAIN_IDS

fun MetaAccount.chainAddress(chain: Chain): String? {
    return when {
        chain.isUniversalWalletBitcoin() -> chainAccounts.universalWalletChainAccount(chain)?.publicKey?.let(chain::bitcoinAddressFromPublicKey)
        chain.isUniversalWalletSolana() -> chainAccounts.universalWalletChainAccount(chain)?.publicKey?.let(chain::solanaAddressFromPublicKey)
        chain.isUniversalWalletIroha() -> chainAccounts.universalWalletChainAccount(chain)?.publicKey?.let(chain::irohaAddressFromPublicKey)
        hasChainAccount(chain.id) -> chain.addressOf(chainAccounts.getValue(chain.id).accountId)
        else -> null
    }
}

fun MetaAccount.accountId(chain: IChain): ByteArray? {
    return when {
        chain is Chain && chain.isUniversalWalletChain() -> chainAccounts.universalWalletChainAccount(chain)?.accountId
        hasChainAccount(chain.id) -> chainAccounts.getValue(chain.id).accountId
        chain.ecosystem == Ecosystem.Substrate -> substrateAccountId
        chain.ecosystem == Ecosystem.Ethereum || chain.ecosystem == Ecosystem.EthereumBased -> ethereumAddress
        //Attention!!! Use tonPublicKey as accountId only internally in fearless wallet. For api requests use ByteArray.tonAccountId(): String function extension
        chain.ecosystem == Ecosystem.Ton -> tonPublicKey
        else -> null
    }
}

private fun Chain.isUniversalWalletChain(): Boolean {
    return isUniversalWalletBitcoin() || isUniversalWalletSolana() || isUniversalWalletIroha()
}

private fun Map<ChainId, MetaAccount.ChainAccount>.universalWalletChainAccount(
    chain: Chain
): MetaAccount.ChainAccount? {
    return chain.universalWalletChainIds().firstNotNullOfOrNull { get(it) }
}

private fun Map<ChainId, LightMetaAccount.UniversalWalletChainAccount>.universalWalletChainAccount(
    chain: Chain
): LightMetaAccount.UniversalWalletChainAccount? {
    return chain.universalWalletChainIds().firstNotNullOfOrNull { get(it) }
}

private fun Chain.universalWalletChainIds(): Set<ChainId> {
    return when {
        isUniversalWalletBitcoin() && (id in BITCOIN_TESTNET_CHAIN_IDS || isTestNet) -> BITCOIN_TESTNET_CHAIN_IDS
        isUniversalWalletBitcoin() -> BITCOIN_MAINNET_CHAIN_IDS
        isUniversalWalletSolana() && (id in SOLANA_DEVNET_CHAIN_IDS || isTestNet) -> SOLANA_DEVNET_CHAIN_IDS
        isUniversalWalletSolana() -> SOLANA_MAINNET_CHAIN_IDS
        isUniversalWalletIroha() && id in TAIRA_CHAIN_IDS -> TAIRA_CHAIN_IDS
        isUniversalWalletIroha() && id in NEXUS_CHAIN_IDS -> NEXUS_CHAIN_IDS
        isUniversalWalletIroha() && isTestNet -> TAIRA_CHAIN_IDS
        else -> emptySet()
    }
}

val MetaAccount.hasSubstrate
    get() = substrateAccountId != null

val MetaAccount.hasEthereum
    get() = ethereumPublicKey != null

val MetaAccount.hasTon
    get() = tonPublicKey != null

val LightMetaAccount.hasSubstrate
    get() = substrateAccountId != null

val LightMetaAccount.hasEthereum
    get() = ethereumPublicKey != null

val LightMetaAccount.hasTon
    get() = tonPublicKey != null
