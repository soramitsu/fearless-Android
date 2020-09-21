package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list

import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.model.BalanceModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.toUiModel

class BalanceListViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter
) : BaseViewModel() {

    val balanceLiveData = getBalance().asLiveData()

    private fun getBalance(): Single<BalanceModel> {
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