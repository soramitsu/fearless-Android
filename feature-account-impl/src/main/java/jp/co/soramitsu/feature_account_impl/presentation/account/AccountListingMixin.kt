package jp.co.soramitsu.feature_account_impl.presentation.account

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.disposables.CompositeDisposable

class AccountListing(val groupedAccounts: List<Any>, val selectedAccount: AccountModel)

interface AccountListingMixin {
    val accountListingDisposable: CompositeDisposable

    val selectedAccountLiveData: MutableLiveData<AccountModel>

    val accountListingLiveData: LiveData<AccountListing>
}