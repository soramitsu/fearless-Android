package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list

import android.graphics.drawable.PictureDrawable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.model.BalanceModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.toUiModel

// TODO use dp
private const val ICON_SIZE_IN_PX = 40

class BalanceListViewModel(
    private val interactor: WalletInteractor,
    private val accountRepository: AccountRepository,
    private val iconGenerator: IconGenerator,
    private val router: WalletRouter
) : BaseViewModel() {
    val userIconLiveData = getUserIcon().asLiveData { showError(it.message!!) }

    // TODO repeating code
    private fun getUserIcon(): Observable<PictureDrawable> {
        return accountRepository.observeSelectedAccount()
            .map { accountRepository.getAddressId(it).blockingGet() }
            .subscribeOn(Schedulers.io())
            .map { iconGenerator.getSvgImage(it, ICON_SIZE_IN_PX) }
            .observeOn(AndroidSchedulers.mainThread())
    }

    val balanceLiveData = getBalance().asLiveData()

    private fun getBalance(): Observable<BalanceModel> {
        return interactor.getAssets()
            .subscribeOn(Schedulers.io())
            .map { it.map(Asset::toUiModel) }
            .map(::BalanceModel)
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun assetClicked() {
        // TODO
    }
}