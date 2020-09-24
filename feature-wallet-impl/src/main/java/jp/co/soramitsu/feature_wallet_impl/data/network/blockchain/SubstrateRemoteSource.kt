package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain

import io.reactivex.Single
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo

interface SubstrateRemoteSource {
    fun fetchAccountInfo(account: Account, node: Node): Single<EncodableStruct<AccountInfo>>
}