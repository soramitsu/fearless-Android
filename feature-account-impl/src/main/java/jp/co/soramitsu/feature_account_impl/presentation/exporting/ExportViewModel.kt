package jp.co.soramitsu.feature_account_impl.presentation.exporting

import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.SecuritySource
import jp.co.soramitsu.feature_account_impl.presentation.common.mapCryptoTypeToCryptoTypeModel
import jp.co.soramitsu.feature_account_impl.presentation.common.mapNetworkToNetworkModel

abstract class ExportViewModel(
    protected val accountInteractor: AccountInteractor,
    protected val accountAddress: String,
    protected val resourceManager: ResourceManager,
    val exportSource: ExportSource
) : BaseViewModel() {
    protected val securityTypeLiveData = loadSecuritySource().asLiveData()

    private val account = loadAccount().asLiveData()

    val cryptoTypeLiveData = account.map { mapCryptoTypeToCryptoTypeModel(resourceManager, it.cryptoType) }

    val networkTypeLiveData = account.map { mapNetworkToNetworkModel(it.network) }

    private fun loadAccount(): Single<Account> {
        return accountInteractor.getAccount(accountAddress)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    private fun loadSecuritySource(): Single<SecuritySource> {
        return accountInteractor.getSecuritySource(accountAddress)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }
}