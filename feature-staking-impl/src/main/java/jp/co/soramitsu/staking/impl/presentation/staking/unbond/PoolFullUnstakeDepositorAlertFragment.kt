package jp.co.soramitsu.staking.impl.presentation.staking.unbond

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.fontSize
import jp.co.soramitsu.common.compose.theme.soraTextStyle
import jp.co.soramitsu.common.compose.theme.weight
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import javax.inject.Inject

@AndroidEntryPoint
class PoolFullUnstakeDepositorAlertFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var navigator: StakingRouter

    companion object {
        const val KEY_PAYLOAD = "payload"
        const val KEY_RESULT = "unstake_result"

        fun getBundle(amount: String) = bundleOf(KEY_PAYLOAD to amount)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.CustomBottomSheetDialogTheme)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setupBottomSheet()
        val amount = requireNotNull(requireArguments().getString(KEY_PAYLOAD))

        return ComposeView(requireContext()).apply {
            setContent {
                FearlessTheme {
                    BottomSheetScreen {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                tint = white,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.End)
                                    .clickable(onClick = ::onCloseClicked)
                            )
                            MarginVertical(margin = 44.dp)
                            GradientIcon(
                                iconRes = R.drawable.ic_status_warning_16,
                                color = alertYellow,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                contentPadding = PaddingValues(bottom = 6.dp)
                            )

                            MarginVertical(margin = 8.dp)
                            H3(text = stringResource(R.string.pool_depositor_unstake_error_title), modifier = Modifier.align(Alignment.CenterHorizontally))
                            MarginVertical(margin = 8.dp)
                            Text(
                                textAlign = TextAlign.Center,
                                text = stringResource(id = R.string.pool_depositor_unstake_error_message, amount),
                                style = soraTextStyle().fontSize(13.sp).weight(FontWeight.Normal),
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                color = black2
                            )
                            MarginVertical(margin = 24.dp)
                            AccentButton(
                                text = stringResource(id = R.string.pool_depositor_unstake_polkadot_js_plus_button_text),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                onClick = ::onPolkadotJsPlusClicked
                            )
                            MarginVertical(margin = 12.dp)
                            GrayButton(
                                text = stringResource(id = R.string.common_close),
                                onClick = ::onCloseClicked,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            )
                            MarginVertical(margin = 12.dp)
                        }
                    }
                }
            }
        }
    }

    private fun onPolkadotJsPlusClicked() {
        navigator.setAlertResult(KEY_RESULT, Result.success(Unit))
        dismiss()
    }

    private fun onCloseClicked() {
        navigator.setAlertResult(KEY_RESULT, Result.failure<Unit>(Exception()))
        dismiss()
    }

    private fun setupBottomSheet() {
        dialog?.setOnShowListener {
            val bottomSheetDialog = it as BottomSheetDialog
            setupBehavior(bottomSheetDialog.behavior)
        }
    }

    private fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isDraggable = false
        behavior.isHideable = true
    }
}
