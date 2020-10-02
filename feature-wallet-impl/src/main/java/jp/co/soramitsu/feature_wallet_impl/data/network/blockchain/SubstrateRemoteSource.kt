package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.FeeRemote
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo

class BlockChainException : Exception()

interface SubstrateRemoteSource {
    fun fetchAccountInfo(account: Account, node: Node): Single<EncodableStruct<AccountInfo>>

    fun getTransferFee(account: Account, node: Node, transfer: Transfer): Single<FeeRemote>

    fun performTransfer(account: Account, node: Node, transfer: Transfer, keypair: Keypair): Completable
}