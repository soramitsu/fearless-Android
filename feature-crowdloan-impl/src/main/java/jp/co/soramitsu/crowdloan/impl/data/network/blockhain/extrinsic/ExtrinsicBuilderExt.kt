package jp.co.soramitsu.crowdloan.impl.data.network.blockhain.extrinsic

import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.shared_utils.encrypt.EncryptionType
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.shared_utils.runtime.extrinsic.ExtrinsicBuilder
import java.math.BigInteger

fun ExtrinsicBuilder.contribute(
    parachainId: ParaId,
    contribution: BigInteger,
    signature: String? = null,
    encryptionType: EncryptionType? = null
): ExtrinsicBuilder = call(
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

fun ExtrinsicBuilder.addMemo(parachainId: ParaId, memo: ByteArray): ExtrinsicBuilder = call(
    moduleName = "Crowdloan",
    callName = "add_memo",
    arguments = mapOf(
        "index" to parachainId,
        "memo" to memo
    )
)

fun ExtrinsicBuilder.addRemarkWithEvent(remark: String): ExtrinsicBuilder = call(
    moduleName = "System",
    callName = "remark_with_event",
    arguments = mapOf(
        "remark" to remark.toByteArray()
    )
)
