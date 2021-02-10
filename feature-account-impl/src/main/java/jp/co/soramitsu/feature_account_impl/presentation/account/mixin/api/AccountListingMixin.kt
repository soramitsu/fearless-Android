package jp.co.soramitsu.feature_account_impl.presentation.account.mixin.api

import jp.co.soramitsu.feature_account_impl.presentation.account.model.AccountModel
import kotlinx.coroutines.flow.Flow

class AccountListing(val groupedAccounts: List<Any>, val selectedAccount: AccountModel)

interface AccountListingMixin {

    fun selectedAccountFlow(): Flow<AccountModel>

    fun accountListingFlow(): Flow<AccountListing>
}