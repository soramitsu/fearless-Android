package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain

import io.reactivex.Single
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.FeeResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo

interface SubstrateRemoteSource {
    fun fetchAccountInfo(account: Account): Single<EncodableStruct<AccountInfo>>

    fun getTransferFee(
        account: Account,
        transfer: Transfer
    ): Single<FeeResponse>

    fun performTransfer(
        account: Account,
        transfer: Transfer,
        keypair: Keypair
    ): Single<String>
}