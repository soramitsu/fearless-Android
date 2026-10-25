package jp.co.soramitsu.account.impl.domain.account.details

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.IrohaAddressCodec
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

private const val IROHA_PUBLIC_KEY_SIZE = 32

/** Local address projection only; never inserted into the runtime chain registry. */
internal fun offlineNexusAccount(wallet: MetaAccount): AccountInChain? {
    val nexus = UniversalWalletRegistry.nexus
    val taira = UniversalWalletRegistry.taira
    val nexusAccounts = wallet.chainAccounts.filterKeys { it.lowercase() in setOf(nexus.id, nexus.chainId) }.values.toList()
    val accounts = nexusAccounts.ifEmpty {
        wallet.chainAccounts.filterKeys { it.lowercase() in setOf(taira.id, taira.chainId) }.values.toList()
    }
    val source = accounts.firstOrNull() ?: return null
    val invalidIdentity = accounts.any {
        it.cryptoType != CryptoType.ED25519 || it.publicKey.size != IROHA_PUBLIC_KEY_SIZE ||
            !it.accountId.contentEquals(it.publicKey)
    }
    if (invalidIdentity || accounts.any { !it.publicKey.contentEquals(source.publicKey) }) {
        return null
    }

    val address = IrohaAddressCodec.encode(source.publicKey.toHexString(withPrefix = false), nexus.chainDiscriminant)
    val chain = Chain(
        id = nexus.id,
        paraId = null,
        rank = null,
        name = "SORA Nexus",
        minSupportedVersion = null,
        assets = emptyList(),
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = null,
        icon = "",
        addressPrefix = 0,
        isEthereumBased = false,
        isTestNet = false,
        hasCrowdloans = false,
        parentId = null,
        supportStakingPool = false,
        isEthereumChain = false,
        chainlinkProvider = false,
        supportNft = false,
        isUsesAppId = false,
        identityChain = null,
        ecosystem = Ecosystem.Substrate,
        remoteAssetsSource = null
    )
    return AccountInChain(
        chain = chain,
        projection = AccountInChain.Projection(address, source.publicKey.copyOf()),
        from = if (nexusAccounts.isEmpty()) AccountInChain.From.META_ACCOUNT else AccountInChain.From.CHAIN_ACCOUNT,
        name = null,
        hasAccount = true,
        markedAsNotNeed = false,
        isOffline = true
    )
}
