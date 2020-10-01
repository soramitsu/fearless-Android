package jp.co.soramitsu.feature_wallet_impl.data.network.model.response

import jp.co.soramitsu.fearless_utils.wsrpc.request.base.RpcRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import java.math.BigInteger

class FeeRemote(val partialFee: BigInteger, val weight: Long)