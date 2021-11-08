package jp.co.soramitsu.feature_crowdloan_impl.data.network.blockhain.extrinsic

import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import java.math.BigInteger

fun ExtrinsicBuilder.contribute(
    parachainId: ParaId,
    contribution: BigInteger,
    signature: String? = null,
    encryptionType: EncryptionType? = null
): ExtrinsicBuilder {
    return call(
        moduleName = "Crowdloan",
        callName = "contribute",
        arguments = mapOf(
            "index" to parachainId,
            "value" to contribution,
            "signature" to signature?.let {
                DictEnum.Entry(
                    name = encryptionType?.rawName?.capitalize().orEmpty(),
                    value = it.fromHex()
                )
            }
        )
    )
}

fun ExtrinsicBuilder.addMemo(parachainId: ParaId, memo: ByteArray): ExtrinsicBuilder {
    return call(
        moduleName = "Crowdloan",
        callName = "add_memo",
        arguments = mapOf(
            "index" to parachainId,
            "memo" to memo
        )
    )
}
