@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain

import io.reactivex.Single
import jp.co.soramitsu.common.data.network.rpc.RxWebSocket
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.common.data.network.scale.invoke
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.account.AccountInfoRequest
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.feeFrozen
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.free
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.miscFrozen
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.reserved
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo.data
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo.nonce
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo.refCount
import org.spongycastle.util.encoders.Hex

class WssSubstrateSource(private val rxWebSocket: RxWebSocket) : SubstrateRemoteSource {

    override fun fetchAccountInfo(account: Account, node: Node): Single<EncodableStruct<AccountInfo>> {
        val publicKey = account.publicKey

        val publicKeyBytes = Hex.decode(publicKey)
        val request = AccountInfoRequest(publicKeyBytes)

        return rxWebSocket.requestWithScaleResponse(request, node.link, AccountInfo)
            .map { response -> response.result ?: emptyAccountInfo() }
    }

    private fun emptyAccountInfo() = AccountInfo { info ->
        info[nonce] = 0.toUInt()
        info[refCount] = 0.toUByte()

        info[data] = AccountData { data ->
            data[free] = 0.toBigInteger()
            data[reserved] = 0.toBigInteger()
            data[miscFrozen] = 0.toBigInteger()
            data[feeFrozen] = 0.toBigInteger()
        }
    }
}