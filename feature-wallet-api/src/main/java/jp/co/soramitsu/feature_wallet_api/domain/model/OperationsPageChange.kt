package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.common.data.model.CursorPage

data class OperationsPageChange(
    val cursorPage: CursorPage<Operation>,
    val accountChanged: Boolean
)
