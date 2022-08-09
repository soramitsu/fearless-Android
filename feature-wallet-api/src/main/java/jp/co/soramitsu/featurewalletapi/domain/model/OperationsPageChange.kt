package jp.co.soramitsu.featurewalletapi.domain.model

import jp.co.soramitsu.common.data.model.CursorPage

data class OperationsPageChange(
    val cursorPage: CursorPage<Operation>,
    val accountChanged: Boolean
)
