package jp.co.soramitsu.featureaccountapi.presentation.exporting

import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

data class ExportSourceChooserPayload(
    val chainId: ChainId,
    val sources: Set<ExportSource>
)
