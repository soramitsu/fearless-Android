package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain

import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadataFlow
import jp.co.soramitsu.feature_crowdloan_impl.BuildConfig

fun mapParachainMetadataRemoteToParachainMetadata(parachainMetadata: ParachainMetadataRemote) =
    with(parachainMetadata) {
        ParachainMetadata(
            iconLink = icon,
            name = name,
            description = description,
            rewardRate = rewardRate?.toBigDecimal(),
            website = website,
            token = token,
            flow = flow?.let { mapParachainMetadataFlowRemoteToParachainMetadataFlow(it) }
        )
    }

fun mapParachainMetadataFlowRemoteToParachainMetadataFlow(flow: ParachainMetadataFlowRemote) =
    with(flow) {
        ParachainMetadataFlow(
            name = name,
            data = data?.let { mapmapParachainMetadataFlowDataRemoteToParachainMetadataFlowData(it) }
        )
    }

fun mapmapParachainMetadataFlowDataRemoteToParachainMetadataFlowData(flowData: Map<String, Any?>): Map<String, Any?> =
    flowData.mapNotNull {
        when (it.key) {
            "devApiUrl" -> if (BuildConfig.DEBUG) FLOW_API_URL to it.value.withoutPrefix() else null
            "devApiKey" -> if (BuildConfig.DEBUG) FLOW_API_KEY to it.value.withoutPrefix() else null
            "prodApiUrl" -> if (!BuildConfig.DEBUG) FLOW_API_URL to it.value.withoutPrefix() else null
            "prodApiKey" -> if (!BuildConfig.DEBUG) FLOW_API_KEY to it.value.withoutPrefix() else null
            else -> it.key to it.value
        }
    }.toMap()

private fun Any?.withoutPrefix(): Any? = (this as? String)?.removePrefix("https://")

