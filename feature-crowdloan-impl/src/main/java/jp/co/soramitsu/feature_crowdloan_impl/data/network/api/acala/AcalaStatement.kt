package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.acala

import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId

class AcalaStatement(
    val paraId: ParaId,
    val statementMsgHash: String,
    val statement: String,
    val proxyAddress: String
)
