package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model

import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationModel

class OperationHistoryElement(
    val displayAddressModel: AddressModel,
    val transactionModel: OperationModel

)
