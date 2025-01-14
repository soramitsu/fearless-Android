package jp.co.soramitsu.tonconnect.impl.presentation.discoverdapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.component.MainToolbar
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.black72
import jp.co.soramitsu.common.utils.hideKeyboard
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject

@AndroidEntryPoint
class DiscoverDappFragment : BaseComposeFragment<DiscoverDappViewModel>() {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    override val viewModel: DiscoverDappViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                FearlessAppTheme {

                    val dappsModalBottomSheetState: ModalBottomSheetState =
                        rememberModalBottomSheetState(
                            initialValue = ModalBottomSheetValue.Hidden,
                            skipHalfExpanded = true
                        )

                    LaunchedEffect(Unit) {
                        viewModel.dappsListBottomSheetState.collect {
                            if (it != null) {
                                dappsModalBottomSheetState.show()
                            } else {
                                dappsModalBottomSheetState.hide()
                            }
                        }
                    }

                    ModalBottomSheetLayout(
                        modifier = Modifier.systemBarsPadding(),
                        sheetState = dappsModalBottomSheetState,
                        sheetBackgroundColor = black72,
                        sheetGesturesEnabled = false,
                        sheetContent = {
                            val dappsListBottomSheetState by viewModel.dappsListBottomSheetState.filterNotNull()
                                .collectAsState(
                                    DappsListState("", emptyList())
                                )
                            BackHandler(enabled = dappsModalBottomSheetState.isVisible, onBack = viewModel::onBottomSheetDappClose)
                            SeeAllDappsBottomSheet(
                                dappsListBottomSheetState,
                                viewModel::bottomSheetDappSelected,
                                viewModel::onBottomSheetDappClose
                            )
                        },
                        content = {
                            val toolbarState by viewModel.toolbarState.collectAsState()
                            val state by viewModel.state.collectAsState()

                            Column {
                                MainToolbar(
                                    state = toolbarState,
                                    menuItems = listOf(
                                        MenuIconItem(
                                            icon = R.drawable.ic_search,
                                            onClick = viewModel::openSearch
                                        )
                                    ),
                                    onChangeChainClick = viewModel::openSelectChain,
                                    onNavigationClick = viewModel::openWalletSelector,
                                    onScoreClick = viewModel::onScoreClick
                                )

                                DiscoverDappScreen(state, viewModel)
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) = Unit

    @ExperimentalMaterialApi
    @Composable
    override fun Toolbar(modalBottomSheetState: ModalBottomSheetState) = Unit

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideKeyboard()
    }
}
