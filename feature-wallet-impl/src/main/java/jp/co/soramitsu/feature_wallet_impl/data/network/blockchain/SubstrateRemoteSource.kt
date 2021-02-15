package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain

import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.BalanceChange
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.FeeResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.ActiveEraInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.StakingLedger
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountInfoSchema
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic.TransferExtrinsic
import kotlinx.coroutines.flow.Flow

interface SubstrateRemoteSource {
    suspend fun fetchAccountInfo(address: String, networkType: Node.NetworkType): EncodableStruct<AccountInfoSchema>

    suspend fun getTransferFee(
        account: Account,
        transfer: Transfer
    ): FeeResponse

    suspend fun performTransfer(
        account: Account,
        transfer: Transfer,
        keypair: Keypair
    ): String

    suspend fun listenForAccountUpdates(
        address: String
    ): Flow<BalanceChange>

    suspend fun listenStakingLedger(
        stashAddress: String
    ): Flow<EncodableStruct<StakingLedger>>

    suspend fun getActiveEra(): EncodableStruct<ActiveEraInfo>

    suspend fun fetchAccountTransfersInBlock(
        blockHash: String,
        account: Account
    ): List<TransferExtrinsic>
}