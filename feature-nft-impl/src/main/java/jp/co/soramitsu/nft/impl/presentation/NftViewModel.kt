package jp.co.soramitsu.nft.impl.presentation

import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel

@HiltViewModel
class NftViewModel @Inject constructor(): BaseViewModel(), NftFlowNavigationCallback {

    override fun onNavigationChanged(navController: NavController, destination: String) {
    }

    override fun onBackPressed(navController: NavController) {
    }

    override fun onFirstScreenClick(navController: NavController) {
        navController.navigate("TODO another screen")
    }

    override fun onSecondScreenClick(navController: NavController) {
        navController.navigate("TODO add start screen")
    }
}