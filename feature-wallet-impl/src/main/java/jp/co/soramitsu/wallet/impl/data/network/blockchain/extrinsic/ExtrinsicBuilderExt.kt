package jp.co.soramitsu.wallet.impl.data.network.blockchain.extrinsic

import jp.co.soramitsu.shared_utils.runtime.extrinsic.ExtrinsicBuilder


fun ExtrinsicBuilder.remark(remark: String): ExtrinsicBuilder = call(
    moduleName = "System",
    callName = "remark",
    arguments = mapOf(
        "remark" to remark.toByteArray()
    )
)