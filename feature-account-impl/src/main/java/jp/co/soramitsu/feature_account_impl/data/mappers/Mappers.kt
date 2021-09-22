package jp.co.soramitsu.feature_account_impl.data.mappers

import android.graphics.drawable.PictureDrawable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.Node.NetworkType
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.core_db.model.chain.ChainAccountLocal
import jp.co.soramitsu.core_db.model.chain.JoinedMetaAccountInfo
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_account_api.data.mappers.stubNetwork
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.addressIn
import jp.co.soramitsu.feature_account_api.domain.model.hexAccountIdIn
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.account.model.AccountModel
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

fun mapNetworkTypeToNetworkModel(networkType: NetworkType): NetworkModel {
    val type = when (networkType) {
        NetworkType.KUSAMA -> NetworkModel.NetworkTypeUI.Kusama
        NetworkType.POLKADOT -> NetworkModel.NetworkTypeUI.Polkadot
        NetworkType.WESTEND -> NetworkModel.NetworkTypeUI.Westend
        NetworkType.ROCOCO -> NetworkModel.NetworkTypeUI.Rococo
    }

    return NetworkModel(networkType.readableName, type)
}

fun mapCryptoTypeToCryptoTypeModel(
    resourceManager: ResourceManager,
    encryptionType: CryptoType
): CryptoTypeModel {

    val name = when (encryptionType) {
        CryptoType.SR25519 -> "${resourceManager.getString(R.string.sr25519_selection_title)} ${resourceManager.getString(
            R.string.sr25519_selection_subtitle
        )}"
        CryptoType.ED25519 -> "${resourceManager.getString(R.string.ed25519_selection_title)} ${resourceManager.getString(
            R.string.ed25519_selection_subtitle
        )}"
        CryptoType.ECDSA -> "${resourceManager.getString(R.string.ecdsa_selection_title)} ${resourceManager.getString(
            R.string.ecdsa_selection_subtitle
        )}"
    }

    return CryptoTypeModel(name, encryptionType)
}

fun mapAccountModelToAccount(accountModel: AccountModel, position: Int = accountModel.position): Account {
    return with(accountModel) {
        Account(
            address,
            name,
            accountIdHex,
            cryptoTypeModel.cryptoType,
            position,
            network,
        )
    }
}

fun mapAccountToAccountModel(
    account: Account,
    accountIcon: PictureDrawable,
    resourceManager: ResourceManager
): AccountModel {
    return with(account) {
        AccountModel(
            address = address,
            name = name,
            image = accountIcon,
            accountIdHex = accountIdHex,
            position = position,
            cryptoTypeModel = mapCryptoTypeToCryptoTypeModel(resourceManager, cryptoType),
            network = network
        )
    }
}

fun mapNodeToNodeModel(node: Node): NodeModel {
    val networkModelType = mapNetworkTypeToNetworkModel(node.networkType)

    return with(node) {
        NodeModel(
            id = id,
            name = name,
            link = link,
            networkModelType = networkModelType.networkTypeUI,
            isDefault = isDefault,
            isActive = isActive
        )
    }
}

fun mapNodeLocalToNode(nodeLocal: NodeLocal): Node {
    return with(nodeLocal) {
        Node(
            id = id,
            name = name,
            networkType = NetworkType.values()[nodeLocal.networkType],
            link = link,
            isActive = isActive,
            isDefault = isDefault
        )
    }
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
                chain = chainsById.getValue(it.chainId),
                publicKey = it.publicKey,
                accountId = it.accountId,
                cryptoType = it.cryptoType
            )
        }
    )

    return with(joinedMetaAccountInfo.metaAccount) {
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
}

fun mapMetaAccountToAccount(chain: Chain, metaAccount: MetaAccount): Account {
    return Account(
        address = metaAccount.addressIn(chain)!!, // TODO in case of ethereum chain may be null,
        name = metaAccount.name,
        accountIdHex = metaAccount.hexAccountIdIn(chain)!!, // TODO in case of ethereum chain may be null,
        cryptoType = metaAccount.substrateCryptoType,
        position = 0,
        network = stubNetwork(chain.id),
    )
}

fun mapChainAccountToAccount(
    parent: MetaAccount,
    chainAccount: MetaAccount.ChainAccount,
): Account {
    val chain = chainAccount.chain

    return Account(
        address = chain.addressOf(chainAccount.accountId),
        name = parent.name,
        accountIdHex = chainAccount.accountId.toHexString(),
        cryptoType = chainAccount.cryptoType,
        position = 0,
        network = stubNetwork(chain.id),
    )
}
