package jp.co.soramitsu.crowdloan.impl.data.network.api.parachain

import java.math.BigInteger

class ParachainMetadataRemote(
    val description: String,
    val icon: String,
    val name: String,
    val paraid: BigInteger,
    val token: String,
    val rewardRate: Double?,
    val website: String,
    val disabled: Boolean = false,
    val flow: ParachainMetadataFlowRemote?
)

data class ParachainMetadataFlowRemote(
    val name: String?,
    val data: Map<String, Any?>?
)

const val FLOW_API_URL = "apiUrl"
const val FLOW_API_KEY = "apiKey"
const val FLOW_BONUS_URL = "bonusUrl"
const val FLOW_BONUS_RATE = "bonusRate"
const val FLOW_TOTAL_REWARD = "totalReward"
const val FLOW_TERMS_URL = "termsUrl"
const val FLOW_CROWDLOAN_INFO_URL = "crowdloanInfoUrl"
const val FLOW_FEARLESS_REFERRAL = "fearlessReferral"
