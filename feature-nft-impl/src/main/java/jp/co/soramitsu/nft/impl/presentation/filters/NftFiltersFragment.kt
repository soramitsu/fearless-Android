package jp.co.soramitsu.nft.impl.presentation.filters

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import javax.inject.Inject
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
import jp.co.soramitsu.nft.impl.presentation.NftRouter

@AndroidEntryPoint
class NftFiltersFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var navigator: NftRouter

    companion object {
        const val KEY_PAYLOAD = "nft_filters_payload"
        const val KEY_RESULT = "nft_filters_result"

        fun getBundle(payload: NftFilterModel): Bundle {
            return bundleOf(KEY_PAYLOAD to payload.bundle)
        }
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

        val filtersBundle = requireNotNull(requireArguments().getBundle(KEY_PAYLOAD))
        val state = NftFilterModel.fromBundle(filtersBundle)

        return ComposeView(requireContext()).apply {
            setContent {
                FearlessTheme {
                    BottomSheetScreen {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                        ) {
                            Row {

                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    tint = white,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.End)
                                        .clickable(onClick = ::onCloseClicked)
                                )

                            }
                        }
                    }
                }
            }
        }
    }

//    private fun onPolkadotJsPlusClicked() {
//        navigator.setAlertResult(KEY_RESULT, Result.success(Unit))
//        dismiss()
//    }
//
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
