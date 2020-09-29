package jp.co.soramitsu.feature_wallet_impl.presentation

import jp.co.soramitsu.feature_wallet_api.domain.model.Asset

interface WalletRouter {
    fun openAssetDetails(token: Asset.Token)

    fun back()
}