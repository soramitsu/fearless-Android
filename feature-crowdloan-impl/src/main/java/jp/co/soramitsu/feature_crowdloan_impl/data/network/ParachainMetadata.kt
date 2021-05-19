package jp.co.soramitsu.feature_crowdloan_impl.data.network

import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata

fun mapParachainMetadataRemoteToParachainMetadata(parachainMetadata: ParachainMetadataRemote) =
    with(parachainMetadata) {
        ParachainMetadata(
            iconLink = icon,
            name = name,
            description = description
        )
    }
