package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model

import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation

class OperationHistoryElement(
    val displayAddressModel: AddressModel,
    val transactionModel: Operation
)
