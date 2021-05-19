package jp.co.soramitsu.feature_crowdloan_impl.data.network

import java.math.BigInteger

class ParachainMetadataRemote(
    val description: String,
    val icon: String,
    val name: String,
    val paraid: BigInteger,
    val website: String
)
