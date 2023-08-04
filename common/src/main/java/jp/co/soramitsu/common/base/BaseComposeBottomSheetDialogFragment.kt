package jp.co.soramitsu.common.base

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.LiveData
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.emptyClick
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.EventObserver

abstract class BaseComposeBottomSheetDialogFragment<T : BaseViewModel> : BottomSheetDialogFragment() {

    abstract val viewModel: T

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.CustomBottomSheetDialogTheme)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setupBottomSheet()
        return ComposeView(requireContext()).apply {
            setContent {
                FearlessTheme {
                    Box(
                        modifier = Modifier
                            .semantics {
                                testTagsAsResourceId = true
                            }
                    ) {
                        Content(PaddingValues())
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.messageLiveData.observeEvent(::showMessage)
        viewModel.errorLiveData.observeEvent {
            showErrorDialog(resources.getString(R.string.common_error_general_title), it)
        }
        viewModel.errorWithTitleLiveData.observeEvent { (title, message) ->
            showErrorDialog(title, message)
        }
        viewModel.errorDialogStateLiveData.observeEvent { errorDialogState ->
            showErrorDialog(
                title = errorDialogState.title,
                message = errorDialogState.message,
                buttonsOrientation = errorDialogState.buttonsOrientation,
                positiveButtonText = errorDialogState.positiveButtonText,
                negativeButtonText = errorDialogState.negativeButtonText,
                positiveClick = errorDialogState.positiveClick
            )
        }
    }

    private fun showErrorDialog(
        title: String,
        message: String,
        positiveButtonText: String? = requireContext().resources.getString(R.string.common_ok),
        negativeButtonText: String? = null,
        buttonsOrientation: Int = LinearLayout.VERTICAL,
        positiveClick: () -> Unit = emptyClick
    ) {
        ErrorDialog(
            title = title,
            message = message,
            buttonsOrientation = buttonsOrientation,
            positiveButtonText = positiveButtonText,
            negativeButtonText = negativeButtonText,
            positiveClick = positiveClick
        ).show(childFragmentManager)
    }

    protected fun showMessage(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT)
            .show()
    }

    @Composable
    abstract fun Content(padding: PaddingValues)

    @Composable
    open fun Toolbar() = Unit

    fun <V> LiveData<V>.observe(observer: (V) -> Unit) {
        observe(viewLifecycleOwner, observer)
    }

    inline fun <V> LiveData<Event<V>>.observeEvent(crossinline observer: (V) -> Unit) {
        observe(
            viewLifecycleOwner,
            EventObserver {
                observer.invoke(it)
            }
        )
    }

    private fun setupBottomSheet() {
        dialog?.setOnShowListener {
            val bottomSheetDialog = it as BottomSheetDialog
            setupBehavior(bottomSheetDialog.behavior)
        }
    }

    protected open fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isDraggable = false
        behavior.isHideable = false
    }
}
