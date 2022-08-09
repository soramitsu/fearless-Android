package jp.co.soramitsu.featurecrowdloanimpl.data.network.api.acala

import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.ParaId

class AcalaStatement(
    val paraId: ParaId,
    val statementMsgHash: String,
    val statement: String,
    val proxyAddress: String
)
