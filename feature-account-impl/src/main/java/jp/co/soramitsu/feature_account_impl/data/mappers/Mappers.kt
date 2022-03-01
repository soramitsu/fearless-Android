package jp.co.soramitsu.feature_account_impl.data.mappers

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core_db.model.chain.ChainAccountLocal
import jp.co.soramitsu.core_db.model.chain.JoinedMetaAccountInfo
import jp.co.soramitsu.core_db.model.chain.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.LightMetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.address
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.runtime.ext.hexAccountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

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

fun mapNodeToNodeModel(node: Chain.Node): NodeModel {
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
): LightMetaAccount = with(metaAccountLocal) {
    LightMetaAccount(
        id = id,
        substratePublicKey = substratePublicKey,
        substrateCryptoType = substrateCryptoType,
        substrateAccountId = substrateAccountId,
        ethereumAddress = ethereumAddress,
        ethereumPublicKey = ethereumPublicKey,
        isSelected = isSelected,
        name = name
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
                chain = chainsById[it.chainId],
                publicKey = it.publicKey,
                accountId = it.accountId,
                cryptoType = it.cryptoType,
                accountName = it.name
            )
        }
    )

    val metaAccount = with(joinedMetaAccountInfo.metaAccount) {
        MetaAccount(
            id = id,
            chainAccounts = chainAccounts,
            substratePublicKey = substratePublicKey,
            substrateCryptoType = substrateCryptoType,
            substrateAccountId = substrateAccountId,
            ethereumAddress = ethereumAddress,
            ethereumPublicKey = ethereumPublicKey,
            isSelected = isSelected,
            name = name
        )
    }
    return metaAccount
}

fun mapMetaAccountToAccount(chain: Chain, metaAccount: MetaAccount): Account? {
    return metaAccount.address(chain)?.let { address ->

        val accountId = chain.hexAccountIdOf(address)

        Account(
            address = address,
            name = metaAccount.name,
            accountIdHex = accountId,
            cryptoType = metaAccount.substrateCryptoType,
            position = 0,
        )
    }
}

fun mapChainAccountToAccount(
    parent: MetaAccount,
    chainAccount: MetaAccount.ChainAccount,
): Account {
    val chain = chainAccount.chain

    return Account(
        address = chain?.let { parent.address(chain) } ?: "Invalid chain (removed)",
        name = parent.name,
        accountIdHex = chainAccount.accountId.toHexString(),
        cryptoType = chainAccount.cryptoType,
        position = 0,
    )
}
