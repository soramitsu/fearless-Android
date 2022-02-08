package jp.co.soramitsu.feature_account_api.presentation.exporting

import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

data class ExportSourceChooserPayload(
    val chainId: ChainId,
    val sources: List<ExportSource>
)
