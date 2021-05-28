package jp.co.soramitsu.feature_crowdloan_impl.data.network.api

import java.math.BigInteger

class ParachainMetadataRemote(
    val description: String,
    val icon: String,
    val name: String,
    val paraid: BigInteger,
    val token: String,
    val rewardRate: Double,
    val customFlow: String?,
    val website: String
)
