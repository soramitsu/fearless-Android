package jp.co.soramitsu.feature_account_impl.presentation.account.model

import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportSource
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

data class ExportSourceChooserPayload(
    val chainId: ChainId,
    val sources: List<ExportSource>
)
