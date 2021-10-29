package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain

import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadataFlow
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadataFlowData

fun mapParachainMetadataRemoteToParachainMetadata(parachainMetadata: ParachainMetadataRemote) =
    with(parachainMetadata) {
        ParachainMetadata(
            iconLink = icon,
            name = name,
            description = description,
            rewardRate = rewardRate?.toBigDecimal(),
            website = website,
            customFlow = customFlow,
            token = token,
            flow = flow?.let { mapParachainMetadataFlowRemoteToParachainMetadataFlow(it) }
        )
    }


fun mapParachainMetadataFlowRemoteToParachainMetadataFlow(flow: ParachainMetadataFlowRemote) =
    with (flow) {
        ParachainMetadataFlow(
            name = name,
            data = mapmapParachainMetadataFlowDataRemoteToParachainMetadataFlowData(data)
        )
    }

fun mapmapParachainMetadataFlowDataRemoteToParachainMetadataFlowData(flowData: ParachainMetadataFlowDataRemote) =
    with (flowData) {
        ParachainMetadataFlowData(
            devApiUrl = devApiUrl,
            devApiKey = devApiKey,
            termsUrl = termsUrl
        )
    }
