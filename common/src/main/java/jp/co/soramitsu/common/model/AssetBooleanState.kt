package jp.co.soramitsu.common.model

import jp.co.soramitsu.core.models.ChainId

data class AssetBooleanState(
    val chainId: ChainId,
    val assetId: String,
    val value: Boolean
)