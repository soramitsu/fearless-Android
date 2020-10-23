package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model

import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel

class TransactionHistoryElement(
    val displayAddressModel: AddressModel,
    val transactionModel: TransactionModel
)