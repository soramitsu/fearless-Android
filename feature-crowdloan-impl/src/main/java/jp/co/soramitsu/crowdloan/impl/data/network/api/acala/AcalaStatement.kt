package jp.co.soramitsu.crowdloan.impl.data.network.api.acala

import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.ParaId

class AcalaStatement(
    val paraId: ParaId,
    val statementMsgHash: String,
    val statement: String,
    val proxyAddress: String
)
