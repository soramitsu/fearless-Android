package jp.co.soramitsu.common.base

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Scaffold
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.EventObserver

abstract class BaseComposeFragment<T : BaseViewModel> : Fragment() {

    abstract val viewModel: T

    @OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterialApi::class)
    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return ComposeView(requireContext()).apply {
            setContent {
                FearlessTheme {
                    val scaffoldState = rememberScaffoldState()
                    val scrollState = rememberScrollState()

                    val keyboardController = LocalSoftwareKeyboardController.current
                    fun hideKeyboardAndConfirm(state: ModalBottomSheetValue): Boolean {
                        if (state == ModalBottomSheetValue.Hidden) {
                            keyboardController?.hide()
                        }
                        return true
                    }

                    val modalBottomSheetState: ModalBottomSheetState = rememberModalBottomSheetState(
                        initialValue = ModalBottomSheetValue.Hidden,
                        skipHalfExpanded = true,
                        confirmValueChange = ::hideKeyboardAndConfirm,
                    )

                    Background()

                    Scaffold(
                        modifier = Modifier.systemBarsPadding(),
                        scaffoldState = scaffoldState,
                        topBar = {
                            Column {
                                Toolbar(modalBottomSheetState)
                            }
                        },
                        content = { padding ->
                            Box(
                                modifier = Modifier
                                    .semantics {
                                        testTagsAsResourceId = true
                                    }
                                    .fillMaxSize()
                                    .padding(padding)
                            ) {
                                Content(padding, scrollState, modalBottomSheetState)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.messageLiveData.observeEvent(::showMessage)
        val errorTitle = resources.getString(R.string.common_error_general_title)
        viewModel.errorLiveData.observeEvent {
            showErrorDialog(errorTitle, it)
        }
        viewModel.errorWithTitleLiveData.observeEvent { (title, message) ->
            showErrorDialog(title, message)
        }
        viewModel.errorDialogStateLiveData.observeEvent { errorDialogState ->
            ErrorDialog(
                title = errorDialogState.title,
                message = errorDialogState.message,
                positiveButtonText = errorDialogState.positiveButtonText,
                negativeButtonText = errorDialogState.negativeButtonText,
                positiveClick = errorDialogState.positiveClick,
                isHideable = errorDialogState.isHideable
            ).show(childFragmentManager)
        }
    }

    private fun showErrorDialog(title: String, message: String) {
        val buttonText = requireContext().resources.getString(R.string.common_ok)
        ErrorDialog(title = title, message = message, positiveButtonText = buttonText).show(childFragmentManager)
    }

    protected fun showMessage(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT)
            .show()
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    abstract fun Content(padding: PaddingValues, scrollState: ScrollState, modalBottomSheetState: ModalBottomSheetState)

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    open fun Toolbar(modalBottomSheetState: ModalBottomSheetState) = Unit

    @Composable
    open fun Background() = Unit

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
}
