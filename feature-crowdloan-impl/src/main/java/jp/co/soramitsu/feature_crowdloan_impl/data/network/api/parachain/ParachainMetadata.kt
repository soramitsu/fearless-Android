package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain

import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadataFlow
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadataFlowData
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

fun mapmapParachainMetadataFlowDataRemoteToParachainMetadataFlowData(flowData: ParachainMetadataFlowDataRemote) =
    with(flowData) {
        ParachainMetadataFlowData(
            apiUrl = if (BuildConfig.DEBUG) devApiUrl else prodApiUrl,
            apiKey = if (BuildConfig.DEBUG) devApiKey else prodApiKey,
            termsUrl = termsUrl
        )
    }
