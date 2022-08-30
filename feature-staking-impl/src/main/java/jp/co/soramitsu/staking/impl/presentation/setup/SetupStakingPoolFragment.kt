package jp.co.soramitsu.staking.impl.presentation.setup

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.AccountInfo
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.FeeInfo
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.backgroundBlack

@AndroidEntryPoint
class SetupStakingPoolFragment : BaseComposeFragment<SetupStakingPoolViewModel>() {
    override val viewModel: SetupStakingPoolViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        val state = viewModel.viewState.collectAsState()

        Column(
            modifier = Modifier
                .imePadding()
                .background(backgroundBlack)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
        ) {
            jp.co.soramitsu.common.compose.component.Toolbar(state = state.value.toolbarViewState, onNavigationClick = viewModel::onNavigationClick)
            MarginVertical(margin = 8.dp)
            AccountInfo(state = state.value.accountInfoState)
            MarginVertical(margin = 12.dp)
            AmountInput(state = state.value.amountInputViewState, onInput = viewModel::onAmountEntered)
            Spacer(modifier = Modifier.weight(1f))
            FeeInfo(state = state.value.feeInfoViewState)
            MarginVertical(margin = 16.dp)
            AccentButton(
                text = state.value.nextButtonTitle, modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp), onClick = viewModel::onNextClick
            )
            MarginVertical(margin = 16.dp)
        }
    }

}
