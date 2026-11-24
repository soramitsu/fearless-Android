package jp.co.soramitsu.account.impl.data.mappers

import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.impl.presentation.node.model.NodeModel
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.chain.FavoriteChainLocal
import jp.co.soramitsu.coredb.model.JoinedMetaAccountInfo
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.ext.hexAccountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.fearless_utils.extensions.toHexString

fun mapCryptoTypeToCryptoTypeModel(
    resourceManager: ResourceManager,
    encryptionType: CryptoType
): CryptoTypeModel {
    val name = when (encryptionType) {
        CryptoType.SR25519 -> "${resourceManager.getString(R.string.sr25519_selection_title)} ${
            resourceManager.getString(
                R.string.sr25519_selection_subtitle
            )
        }"

        CryptoType.ED25519 -> "${resourceManager.getString(R.string.ed25519_selection_title)} ${
            resourceManager.getString(
                R.string.ed25519_selection_subtitle
            )
        }"

        CryptoType.ECDSA -> "${resourceManager.getString(R.string.ecdsa_selection_title)} ${
            resourceManager.getString(
                R.string.ecdsa_selection_subtitle
            )
        }"
    }

    return CryptoTypeModel(name, encryptionType)
}

fun mapNodeToNodeModel(node: ChainNode): NodeModel {
    return with(node) {
        NodeModel(
            name = name,
            link = url,
            isDefault = isDefault,
            isActive = isActive
        )
    }
}

fun mapMetaAccountLocalToLightMetaAccount(
    metaAccountLocal: MetaAccountLocal
): LightMetaAccount = mapMetaAccountLocalToLightMetaAccount(metaAccountLocal, emptyList())

fun mapJoinedMetaAccountInfoToLightMetaAccount(
    joinedMetaAccountInfo: JoinedMetaAccountInfo
): LightMetaAccount = mapMetaAccountLocalToLightMetaAccount(
    joinedMetaAccountInfo.metaAccount,
    joinedMetaAccountInfo.chainAccounts
)

private fun mapMetaAccountLocalToLightMetaAccount(
    metaAccountLocal: MetaAccountLocal,
    chainAccounts: List<ChainAccountLocal>
): LightMetaAccount = with(metaAccountLocal) {
    LightMetaAccount(
        id = id,
        substratePublicKey = substratePublicKey,
        substrateCryptoType = substrateCryptoType,
        substrateAccountId = substrateAccountId,
        ethereumAddress = ethereumAddress,
        ethereumPublicKey = ethereumPublicKey,
        tonPublicKey = tonPublicKey,
        universalWalletChainAccounts = chainAccounts.associateBy(
            keySelector = ChainAccountLocal::chainId,
            valueTransform = {
                LightMetaAccount.UniversalWalletChainAccount(
                    publicKey = it.publicKey,
                    accountId = it.accountId,
                    cryptoType = it.cryptoType
                )
            }
        ),
        isSelected = isSelected,
        name = name,
        isBackedUp = isBackedUp,
        initialized = initialized,
    )
}

fun mapMetaAccountLocalToMetaAccount(
    chainsById: Map<ChainId, Chain>,
    joinedMetaAccountInfo: JoinedMetaAccountInfo
): MetaAccount {
    val chainAccounts = joinedMetaAccountInfo.chainAccounts.associateBy(
        keySelector = ChainAccountLocal::chainId,
        valueTransform = {
            MetaAccount.ChainAccount(
                metaId = joinedMetaAccountInfo.metaAccount.id,
                chain = chainsById.getOrDefault(it.chainId, null),
                publicKey = it.publicKey,
                accountId = it.accountId,
                cryptoType = it.cryptoType,
                accountName = it.name
            )
        }
    )

    val favoriteChains = joinedMetaAccountInfo.favoriteChains.associateBy(
        keySelector = FavoriteChainLocal::chainId,
        valueTransform = {
            MetaAccount.FavoriteChain(
                chain = chainsById.getOrDefault(it.chainId, null),
                isFavorite = it.isFavorite
            )
        }
    )

    val metaAccount = with(joinedMetaAccountInfo.metaAccount) {
        MetaAccount(
            id = id,
            chainAccounts = chainAccounts,
            favoriteChains = favoriteChains,
            substratePublicKey = substratePublicKey,
            substrateCryptoType = substrateCryptoType,
            substrateAccountId = substrateAccountId,
            ethereumAddress = ethereumAddress,
            ethereumPublicKey = ethereumPublicKey,
            tonPublicKey = tonPublicKey,
            isSelected = isSelected,
            name = name,
            isBackedUp = isBackedUp,
            googleBackupAddress = googleBackupAddress,
            initialized = initialized,
        )
    }
    return metaAccount
}

fun mapMetaAccountToAccount(chain: Chain, metaAccount: MetaAccount): Account? {
    if(chain.ecosystem != Ecosystem.Substrate) return null
    return metaAccount.address(chain)?.let { address ->
        runCatching {
            val accountId = chain.hexAccountIdOf(address)

            Account(
                address = address,
                name = metaAccount.name,
                accountIdHex = accountId,
                cryptoType = metaAccount.substrateCryptoType ?: CryptoType.SR25519,
                position = 0
            )
        }.getOrNull()
    }
}

fun mapChainAccountToAccount(
    parent: MetaAccount,
    chainAccount: MetaAccount.ChainAccount
): Account {
    val chain = chainAccount.chain

    return Account(
        address = chain?.let { parent.address(chain) } ?: "Invalid chain (removed)",
        name = parent.name,
        accountIdHex = chainAccount.accountId.toHexString(),
        cryptoType = chainAccount.cryptoType,
        position = 0
    )
}
