package jp.co.soramitsu.feature_wallet_impl.presentation.buy

import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter

class BuyConfiguration(
    val address: String,
    val tokenType: Token.Type,
    val provider: BuyTokenRegistry.Provider<*>
)

class BuyViewModel(
    private val interactor: WalletInteractor,
    private val resourceManager: ResourceManager,
    private val buyTokenRegistry: BuyTokenRegistry,
    private val router: WalletRouter
) : BaseViewModel() {

    val buyConfigurationLiveData = getBuyConfiguration().asLiveData()

    private fun getBuyConfiguration(): Single<BuyConfiguration> {
        return interactor.observeSelectedAccount()
            .firstOrError()
            .subscribeOn(Schedulers.io())
            .map {
                val tokenType = Token.Type.fromNetworkType(it.network.type)
                val provider = buyTokenRegistry.availableProviders(tokenType).first()

                BuyConfiguration(it.address, tokenType, provider)
            }
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun buyCompleted() {
        showMessage(resourceManager.getString(R.string.buy_completed))

        router.back()
    }

    fun backClicked() {
        router.back()
    }
}