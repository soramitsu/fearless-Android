package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model

import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.feature_wallet_api.domain.model.SubqueryElement
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel

class TransactionHistoryElement(
    val displayAddressModel: AddressModel,
    val transactionModel: TransactionModel
)

class NewTransactionHistoryElement(
    val displayAddressModel: AddressModel,
    val transactionModel: SubqueryElement
)
