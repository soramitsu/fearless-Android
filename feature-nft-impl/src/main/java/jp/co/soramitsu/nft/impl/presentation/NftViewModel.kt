package jp.co.soramitsu.nft.impl.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class NftViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(), NftFlowNavigationCallback {

    private val startDestination: String = requireNotNull(savedStateHandle[NftFragment.START_DESTINATION_KEY])

    val state = MutableStateFlow(NFTFlowState(startDestination, startDestination))

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
