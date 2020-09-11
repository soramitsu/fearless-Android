package jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.disposables.CompositeDisposable
import jp.co.soramitsu.feature_account_impl.presentation.accounts.model.AccountModel

interface AccountListingMixin {
    val accountListingDisposable : CompositeDisposable

    val groupedAccountModelsLiveData : LiveData<List<Any>>

    val selectedAccountLiveData : MutableLiveData<AccountModel>
}