package jp.co.soramitsu.common.base

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.SnackbarData
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import jp.co.soramitsu.common.compose.component.CustomSnackbar
import jp.co.soramitsu.common.compose.component.CustomSnackbarType
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.common.utils.showToast
import kotlinx.coroutines.launch

abstract class BaseComposeBottomSheetDialogFragment<T : BaseViewModel>() : BottomSheetDialogFragment(), SnackbarOwnerInterface {

    abstract val viewModel: T

    private lateinit var snackbarHostState: SnackbarHostState

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
                snackbarHostState = remember { SnackbarHostState() }
                FearlessTheme {
                    Box(
                        modifier = Modifier
                            .semantics {
                                testTagsAsResourceId = true
                            }
                    ) {
                        Content(PaddingValues())
                        SnackbarHost(
                            modifier = Modifier.align(Alignment.BottomStart),
                            hostState = snackbarHostState
                        ) { snackbarData: SnackbarData ->
                            CustomSnackbar(snackbarData)
                        }
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.messageLiveData.observeEvent(::showToast)
        viewModel.snackbarLiveData.observeEvent(::showSnackbar)
        viewModel.errorLiveData.observeEvent {
            showErrorDialog(resources.getString(R.string.common_error_general_title), it)
        }
        viewModel.errorWithTitleLiveData.observeEvent { (title, message) ->
            showErrorDialog(title, message)
        }
    }

    private fun showErrorDialog(title: String, message: String) {
        val buttonText = requireContext().resources.getString(R.string.common_ok)
        ErrorDialog(title = title, message = message, positiveButtonText = buttonText).show(childFragmentManager)
    }

    override fun showSnackbar(type: CustomSnackbarType, duration: SnackbarDuration) {
        viewModel.launch {
            val snackbarResult = snackbarHostState.showSnackbar(
                message = type.name,
                duration = duration
            )
            println("!!! Bottom snackbarResult: ${snackbarResult.name}")
        }
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
