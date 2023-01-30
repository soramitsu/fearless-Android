package jp.co.soramitsu.common.presentation

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.Grip
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.emptyClick
import jp.co.soramitsu.common.compose.component.soraTextStyle
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.fontSize
import jp.co.soramitsu.common.compose.theme.weight
import jp.co.soramitsu.common.compose.theme.white

class ErrorDialog(
    private val title: String,
    private val message: String,
    private val positiveButtonText: String? = null,
    private val negativeButtonText: String? = null,
    private val textSize: Int = 13,
    @DrawableRes private val iconRes: Int = R.drawable.ic_status_warning_16,
    private val isHideable: Boolean = true,
    private val onBackClick: () -> Unit = emptyClick,
    private val positiveClick: () -> Unit = emptyClick,
    private val negativeClick: () -> Unit = emptyClick
) : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "errorDialogTag"
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
        return ComposeView(requireContext()).apply {
            setContent {
                FearlessTheme {
                    BottomSheetScreen {
                        Grip(Modifier.align(Alignment.CenterHorizontally))
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                        ) {
                            if (isHideable) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    tint = white,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.End)
                                        .clickable {
                                            onBackClick()
                                            dismiss()
                                        }

                                )
                            }
                            MarginVertical(margin = 44.dp)
                            GradientIcon(
                                iconRes = iconRes,
                                color = alertYellow,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                contentPadding = PaddingValues(bottom = 6.dp)
                            )

                            MarginVertical(margin = 8.dp)
                            H3(text = title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            MarginVertical(margin = 8.dp)
                            Text(
                                textAlign = TextAlign.Center,
                                text = message,
                                style = soraTextStyle().fontSize(textSize.sp).weight(FontWeight.Normal),
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                color = black2
                            )
                            MarginVertical(margin = 24.dp)
                            positiveButtonText?.let {
                                AccentButton(
                                    text = it,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) {
                                    positiveClick()
                                    dismiss()
                                }
                            }
                            negativeButtonText?.let {
                                MarginVertical(margin = 12.dp)
                                GrayButton(
                                    text = it,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) {
                                    negativeClick()
                                    dismiss()
                                }
                            }

                            MarginVertical(margin = 12.dp)
                        }
                    }
                }
            }
        }
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
        behavior.isHideable = isHideable
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }
}
