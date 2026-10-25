package jp.co.soramitsu.common.presentation

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.emptyClick
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.fontSize
import jp.co.soramitsu.common.compose.theme.soraTextStyle
import jp.co.soramitsu.common.compose.theme.weight
import jp.co.soramitsu.common.compose.theme.white

class ErrorDialog() : BottomSheetDialogFragment() {

    enum class Action {
        BACK,
        POSITIVE,
        SECOND_POSITIVE,
        NEGATIVE
    }

    companion object {
        const val TAG = "errorDialogTag"

        private const val ARG_TITLE = "error_dialog_title"
        private const val ARG_MESSAGE = "error_dialog_message"
        private const val ARG_POSITIVE_BUTTON_TEXT = "error_dialog_positive_button_text"
        private const val ARG_SECOND_POSITIVE_BUTTON_TEXT =
            "error_dialog_second_positive_button_text"
        private const val ARG_NEGATIVE_BUTTON_TEXT = "error_dialog_negative_button_text"
        private const val ARG_TEXT_SIZE = "error_dialog_text_size"
        private const val ARG_ICON_RES = "error_dialog_icon_res"
        private const val ARG_IS_HIDEABLE = "error_dialog_is_hideable"
        private const val ARG_BUTTONS_ORIENTATION = "error_dialog_buttons_orientation"
        private const val ARG_RESULT_REQUEST_KEY = "error_dialog_result_request_key"
        private const val ARG_RESULT_PAYLOAD = "error_dialog_result_payload"
        private const val ARG_REQUIRES_EPHEMERAL_CALLBACKS =
            "error_dialog_requires_ephemeral_callbacks"
        private const val STATE_ACTION_DISPATCHED =
            "error_dialog_action_dispatched"

        private const val DEFAULT_TEXT_SIZE = 13

        const val RESULT_ACTION = "error_dialog_result_action"
        const val RESULT_PAYLOAD = "error_dialog_result_payload"

        fun resultAction(result: Bundle): Action? {
            val encoded = try {
                result.getString(RESULT_ACTION)
            } catch (_: RuntimeException) {
                null
            } ?: return null
            return runCatching { Action.valueOf(encoded) }.getOrNull()
        }
    }

    constructor(
        title: String?,
        message: String,
        positiveButtonText: String? = null,
        secondPositiveButtonText: String? = null,
        negativeButtonText: String? = null,
        textSize: Int = DEFAULT_TEXT_SIZE,
        @DrawableRes iconRes: Int = R.drawable.ic_status_warning_16,
        isHideable: Boolean = true,
        buttonsOrientation: Int = LinearLayout.VERTICAL,
        resultRequestKey: String? = null,
        resultPayload: Parcelable? = null,
        onBackClick: () -> Unit = emptyClick,
        positiveClick: () -> Unit = emptyClick,
        secondPositiveClick: () -> Unit = emptyClick,
        negativeClick: () -> Unit = emptyClick
    ) : this() {
        require(resultRequestKey == null || resultRequestKey.isNotBlank()) {
            "A dialog result request key must not be blank"
        }
        arguments = Bundle().apply {
            putString(ARG_TITLE, title)
            putString(ARG_MESSAGE, message)
            putString(ARG_POSITIVE_BUTTON_TEXT, positiveButtonText)
            putString(ARG_SECOND_POSITIVE_BUTTON_TEXT, secondPositiveButtonText)
            putString(ARG_NEGATIVE_BUTTON_TEXT, negativeButtonText)
            putInt(ARG_TEXT_SIZE, textSize)
            putInt(ARG_ICON_RES, iconRes)
            putBoolean(ARG_IS_HIDEABLE, isHideable)
            putInt(ARG_BUTTONS_ORIENTATION, buttonsOrientation)
            putString(ARG_RESULT_REQUEST_KEY, resultRequestKey)
            putParcelable(ARG_RESULT_PAYLOAD, resultPayload)
            putBoolean(
                ARG_REQUIRES_EPHEMERAL_CALLBACKS,
                resultRequestKey == null && (
                    onBackClick !== emptyClick ||
                        positiveClick !== emptyClick ||
                        secondPositiveClick !== emptyClick ||
                        negativeClick !== emptyClick
                    )
            )
        }
        this.onBackClick = onBackClick
        this.positiveClick = positiveClick
        this.secondPositiveClick = secondPositiveClick
        this.negativeClick = negativeClick
    }

    private val title: String?
        get() = arguments?.getString(ARG_TITLE)

    private val message: String
        get() = arguments?.getString(ARG_MESSAGE).orEmpty()

    private val positiveButtonText: String?
        get() = arguments?.getString(ARG_POSITIVE_BUTTON_TEXT)

    private val secondPositiveButtonText: String?
        get() = arguments?.getString(ARG_SECOND_POSITIVE_BUTTON_TEXT)

    private val negativeButtonText: String?
        get() = arguments?.getString(ARG_NEGATIVE_BUTTON_TEXT)

    private val textSize: Int
        get() = arguments?.getInt(ARG_TEXT_SIZE, DEFAULT_TEXT_SIZE) ?: DEFAULT_TEXT_SIZE

    @get:DrawableRes
    private val iconRes: Int
        get() = arguments?.getInt(ARG_ICON_RES, R.drawable.ic_status_warning_16)
            ?: R.drawable.ic_status_warning_16

    private val isHideable: Boolean
        get() = arguments?.getBoolean(ARG_IS_HIDEABLE, true) ?: true

    private val buttonsOrientation: Int
        get() = arguments?.getInt(ARG_BUTTONS_ORIENTATION, LinearLayout.VERTICAL)
            ?: LinearLayout.VERTICAL

    private val resultRequestKey: String?
        get() = arguments?.getString(ARG_RESULT_REQUEST_KEY)

    @Suppress("DEPRECATION")
    private val resultPayload: Parcelable?
        get() = arguments?.getParcelable(ARG_RESULT_PAYLOAD)

    private var onBackClick: () -> Unit = emptyClick
    private var positiveClick: () -> Unit = emptyClick
    private var secondPositiveClick: () -> Unit = emptyClick
    private var negativeClick: () -> Unit = emptyClick
    private var dismissBecauseCallbacksWereLost = false
    private var actionDispatched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.CustomBottomSheetDialogThemeNoIme)
        actionDispatched =
            savedInstanceState?.getBoolean(STATE_ACTION_DISPATCHED, false)
                ?: false
        dismissBecauseCallbacksWereLost =
            savedInstanceState != null &&
                arguments?.getBoolean(
                    ARG_REQUIRES_EPHEMERAL_CALLBACKS,
                    false
                ) == true &&
                !hasRuntimeCallbacks()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_ACTION_DISPATCHED, actionDispatched)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        if (dismissBecauseCallbacksWereLost) {
            // Lambda callbacks cannot survive process/configuration restoration.
            // Fail closed instead of presenting buttons that silently do nothing.
            dismissAllowingStateLoss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return object : BottomSheetDialog(requireContext(), theme) {
            @Deprecated("Deprecated in Java")
            override fun onBackPressed() {
                if (isHideable) {
                    dispatchAction(Action.BACK, onBackClick)
                    super.onBackPressed()
                }
            }
        }
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
                                            dispatchAction(Action.BACK, onBackClick)
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
                            title?.let {
                                H3(text = it, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                MarginVertical(margin = 8.dp)
                            }
                            Text(
                                textAlign = TextAlign.Center,
                                text = message,
                                style = soraTextStyle().fontSize(textSize.sp).weight(FontWeight.Normal),
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                color = black2
                            )
                            MarginVertical(margin = 24.dp)
                            if (buttonsOrientation == LinearLayout.VERTICAL) {
                                positiveButtonText?.let {
                                    AccentButton(
                                        text = it,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                    ) {
                                        dispatchAction(Action.POSITIVE, positiveClick)
                                        dismiss()
                                    }
                                    MarginVertical(margin = 12.dp)
                                }
                                secondPositiveButtonText?.let {
                                    GrayButton(
                                        text = it,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                    ) {
                                        dispatchAction(
                                            Action.SECOND_POSITIVE,
                                            secondPositiveClick
                                        )
                                        dismiss()
                                    }
                                    MarginVertical(margin = 12.dp)
                                }
                                negativeButtonText?.let {
                                    GrayButton(
                                        text = it,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                    ) {
                                        dispatchAction(Action.NEGATIVE, negativeClick)
                                        dismiss()
                                    }
                                    MarginVertical(margin = 12.dp)
                                }
                            } else {
                                Row {
                                    negativeButtonText?.let {
                                        GrayButton(
                                            text = it,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(52.dp)
                                        ) {
                                            dispatchAction(Action.NEGATIVE, negativeClick)
                                            dismiss()
                                        }
                                    }
                                    MarginHorizontal(margin = 12.dp)
                                    positiveButtonText?.let {
                                        AccentButton(
                                            text = it,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(52.dp)
                                        ) {
                                            dispatchAction(Action.POSITIVE, positiveClick)
                                            dismiss()
                                        }
                                    }
                                }
                                MarginVertical(margin = 12.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupBottomSheet() {
        dialog?.setOnShowListener {
            val bottomSheetDialog = it as BottomSheetDialog
            bottomSheetDialog.setCanceledOnTouchOutside(
                isHideable &&
                    resultRequestKey == null &&
                    onBackClick === emptyClick
            )
            setupBehavior(bottomSheetDialog.behavior)
        }
    }

    private fun dispatchAction(action: Action, callback: () -> Unit) {
        if (actionDispatched) {
            return
        }
        actionDispatched = true

        val requestKey = resultRequestKey
        if (requestKey == null) {
            callback()
        } else {
            parentFragmentManager.setFragmentResult(
                requestKey,
                bundleOf(
                    RESULT_ACTION to action.name,
                    RESULT_PAYLOAD to resultPayload
                )
            )
        }
    }

    private fun hasRuntimeCallbacks(): Boolean =
        onBackClick !== emptyClick ||
            positiveClick !== emptyClick ||
            secondPositiveClick !== emptyClick ||
            negativeClick !== emptyClick

    private fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isDraggable = false
        behavior.isHideable = isHideable
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }
}
