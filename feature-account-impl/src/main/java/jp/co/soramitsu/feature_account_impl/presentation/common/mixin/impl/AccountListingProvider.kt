package jp.co.soramitsu.feature_account_impl.presentation.common.mixin.impl

import android.graphics.drawable.PictureDrawable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.utils.asLiveData
import jp.co.soramitsu.common.utils.asMutableLiveData
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_impl.presentation.accounts.model.AccountModel
import jp.co.soramitsu.feature_account_impl.presentation.common.mapNetworkToNetworkModel
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.AccountListingMixin

private const val ICON_SIZE_IN_PX = 50

class AccountListingProvider(
    private val accountInteractor: AccountInteractor,
    private val iconGenerator: IconGenerator
) : AccountListingMixin {
    override val accountListingDisposable: CompositeDisposable = CompositeDisposable()

    override val groupedAccountModelsLiveData =
        getGroupedAccounts().asLiveData(accountListingDisposable)

    override val selectedAccountLiveData = getSelectedAccountModel()
        .asMutableLiveData(accountListingDisposable)

    private fun getSelectedAccountModel() = accountInteractor.getSelectedAccount()
        .subscribeOn(Schedulers.computation())
        .map(::transformAccount)
        .observeOn(AndroidSchedulers.mainThread())

    private fun getGroupedAccounts() = accountInteractor.getAccountsWithNetworks()
        .subscribeOn(Schedulers.computation())
        .map(::transformToModels)
        .subscribeOn(Schedulers.computation())

    private fun transformToModels(list: List<Any>): List<Any> {
        return list.map {
            val mapped: Any = when (it) {
                is Account -> transformAccount(it)
                is Network -> mapNetworkToNetworkModel(it)
                else -> throw IllegalArgumentException()
            }

            mapped
        }
    }

    private fun transformAccount(account: Account): AccountModel {
        val picture = generateIcon(account)

        return AccountModel(account.address, account.shortAddress, account.name, picture)
    }

    private fun generateIcon(account: Account): PictureDrawable {
        return accountInteractor.getAddressId(account)
            .map { iconGenerator.getSvgImage(it, ICON_SIZE_IN_PX) }
            .blockingGet()
    }
}