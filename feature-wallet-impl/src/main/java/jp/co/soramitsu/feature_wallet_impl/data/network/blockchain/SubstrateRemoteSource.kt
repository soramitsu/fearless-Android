package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain

import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.BalanceChange
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.FeeResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.ActiveEraInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.StakingLedger
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SubmittableExtrinsic

interface SubstrateRemoteSource {
    fun fetchAccountInfo(address: String, networkType: Node.NetworkType): Single<EncodableStruct<AccountInfo>>

    fun getTransferFee(
        account: Account,
        transfer: Transfer
    ): Single<FeeResponse>

    fun performTransfer(
        account: Account,
        transfer: Transfer,
        keypair: Keypair
    ): Single<String>

    fun listenForAccountUpdates(
        account: Account
    ): Observable<BalanceChange>

    fun listenStakingLedger(
        account: Account
    ): Observable<EncodableStruct<StakingLedger>>

    fun getActiveEra(): Single<EncodableStruct<ActiveEraInfo>>

    fun fetchAccountTransactionInBlock(
        blockHash: String,
        account: Account
    ): Single<List<EncodableStruct<SubmittableExtrinsic>>>
}