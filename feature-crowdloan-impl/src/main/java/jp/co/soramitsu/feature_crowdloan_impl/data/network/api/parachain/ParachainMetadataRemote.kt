package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain

import java.math.BigInteger

class ParachainMetadataRemote(
    val description: String,
    val icon: String,
    val name: String,
    val paraid: BigInteger,
    val token: String,
    val rewardRate: Double?,
    val customFlow: String?,
    val website: String,
    val flow: ParachainMetadataFlowRemote?
)

data class ParachainMetadataFlowRemote(
    val name: String,
    val data: ParachainMetadataFlowDataRemote
)

data class ParachainMetadataFlowDataRemote(
    val devApiUrl: String,
    val devApiKey: String,
    val prodApiUrl: String,
    val prodApiKey: String,
    val termsUrl: String,
)
