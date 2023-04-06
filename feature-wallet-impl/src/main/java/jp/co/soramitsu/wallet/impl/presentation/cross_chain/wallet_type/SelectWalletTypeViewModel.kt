package jp.co.soramitsu.wallet.impl.presentation.cross_chain.wallet_type

import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import javax.inject.Inject

@HiltViewModel
class SelectWalletTypeViewModel @Inject constructor(
    private val router: WalletRouter
) : BaseViewModel(), SelectWalletTypeScreenInterface {

    override fun onNavigationClick() {
        router.back()
    }

    override fun onMyWalletClick() {
        router.backWithResult(
            SelectWalletTypeFragment.KEY_WALLET_TYPE to WalletType.My
        )
    }

    override fun onExternalWalletClick() {
        router.backWithResult(
            SelectWalletTypeFragment.KEY_WALLET_TYPE to WalletType.External
        )
    }
}
