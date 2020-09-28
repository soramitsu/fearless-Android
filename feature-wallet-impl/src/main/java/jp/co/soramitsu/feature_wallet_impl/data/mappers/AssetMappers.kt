package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.Transfer

fun AssetLocal.toAsset(): Asset {
    return Asset(
        token = token,
        balanceInPlanks = balanceInPlanks,
        dollarRate = dollarRate,
        recentRateChange = recentRateChange
    )
}

fun Asset.toLocal(accountAddress: String): AssetLocal {
    return AssetLocal(accountAddress = accountAddress,
        token = token,
        balanceInPlanks = balanceInPlanks,
        dollarRate = dollarRate,
        recentRateChange = recentRateChange
    )
}