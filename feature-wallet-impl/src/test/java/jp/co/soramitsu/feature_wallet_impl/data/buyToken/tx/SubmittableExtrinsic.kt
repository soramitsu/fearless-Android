package jp.co.soramitsu.feature_wallet_impl.data.buyToken.tx

import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.SubstrateApi

class SubmittableExtrinsic(
    call: GenericCall.Instance,
    private val api: SubstrateApi,
) : GenericCall.Instance by call {

    suspend fun submit(keypair: Keypair): String {
        return TODO()
    }
}
