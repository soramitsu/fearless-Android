package jp.co.soramitsu.feature_account_impl.data.mappers

import android.graphics.drawable.PictureDrawable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.account.model.AccountModel
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel

fun mapNetworkTypeToNetworkModel(networkType: jp.co.soramitsu.domain.model.Node.NetworkType): NetworkModel {
    val type = when (networkType) {
        jp.co.soramitsu.domain.model.Node.NetworkType.KUSAMA -> NetworkModel.NetworkTypeUI.Kusama
        jp.co.soramitsu.domain.model.Node.NetworkType.POLKADOT -> NetworkModel.NetworkTypeUI.Polkadot
        jp.co.soramitsu.domain.model.Node.NetworkType.WESTEND -> NetworkModel.NetworkTypeUI.Westend
    }

    return NetworkModel(networkType.readableName, type)
}

fun mapCryptoTypeToCryptoTypeModel(
    resourceManager: ResourceManager,
    encryptionType: jp.co.soramitsu.domain.model.CryptoType
): CryptoTypeModel {

    val name = when (encryptionType) {
        jp.co.soramitsu.domain.model.CryptoType.SR25519 -> "${resourceManager.getString(R.string.sr25519_selection_title)} ${resourceManager.getString(
            R.string.sr25519_selection_subtitle
        )}"
        jp.co.soramitsu.domain.model.CryptoType.ED25519 -> "${resourceManager.getString(R.string.ed25519_selection_title)} ${resourceManager.getString(
            R.string.ed25519_selection_subtitle
        )}"
        jp.co.soramitsu.domain.model.CryptoType.ECDSA -> "${resourceManager.getString(R.string.ecdsa_selection_title)} ${resourceManager.getString(
            R.string.ecdsa_selection_subtitle
        )}"
    }

    return CryptoTypeModel(name, encryptionType)
}

fun mapAccountModelToAccount(accountModel: AccountModel, position: Int = accountModel.position): Account {
    return with(accountModel) {
        Account(address, name, publicKey, cryptoTypeModel.cryptoType, position, network)
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
            publicKey = publicKey,
            position = position,
            cryptoTypeModel = mapCryptoTypeToCryptoTypeModel(resourceManager, cryptoType),
            network = network
        )
    }
}

fun mapNodeToNodeModel(node: jp.co.soramitsu.domain.model.Node): NodeModel {
    val networkModelType = when (node.networkType) {
        jp.co.soramitsu.domain.model.Node.NetworkType.KUSAMA -> NetworkModel.NetworkTypeUI.Kusama
        jp.co.soramitsu.domain.model.Node.NetworkType.POLKADOT -> NetworkModel.NetworkTypeUI.Polkadot
        jp.co.soramitsu.domain.model.Node.NetworkType.WESTEND -> NetworkModel.NetworkTypeUI.Westend
    }

    return with(node) {
        NodeModel(
            id = id,
            name = name,
            link = link,
            networkModelType = networkModelType,
            isDefault = isDefault
        )
    }
}