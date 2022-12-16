package jp.co.soramitsu.common.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.compose.material.SnackbarDuration
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.CustomSnackbarType
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.dp
import jp.co.soramitsu.common.utils.showToast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector

abstract class BaseBottomSheetDialogFragment<T : BaseViewModel>(@LayoutRes private val layoutRes: Int) : BottomSheetDialogFragment(), SnackbarShowerInterface {

    abstract val viewModel: T

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return provideView()
    }

    abstract fun provideView(): View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBottomSheet()

        initViews()
        subscribe(viewModel)

        viewModel.errorLiveData.observeEvent(::showError)

        viewModel.errorWithTitleLiveData.observeEvent {
            showErrorWithTitle(it.first, it.second)
        }

        viewModel.messageLiveData.observeEvent(::showToast)
        viewModel.snackbarLiveData.observeEvent(::showSnackbar)
    }

     override fun showSnackbar(snackbarType: CustomSnackbarType, duration: SnackbarDuration) {
        (activity as? BaseActivity<*>)?.showSnackbar(snackbarType)
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

    protected fun showError(errorMessage: String) {
        buildErrorDialog(getString(R.string.common_error_general_title), errorMessage)
            .show(childFragmentManager)
    }

    protected fun showErrorWithTitle(title: String, errorMessage: String) {
        buildErrorDialog(title, errorMessage).show(childFragmentManager)
    }

    protected open fun buildErrorDialog(title: String, errorMessage: String): ErrorDialog {
        val res = requireContext().resources
        return ErrorDialog(
            title = title,
            message = errorMessage,
            positiveButtonText = res.getString(R.string.common_ok)
        )
    }

    inline fun <V> LiveData<Event<V>>.observeEvent(crossinline observer: (V) -> Unit) {
        observe(
            viewLifecycleOwner,
            EventObserver {
                observer.invoke(it)
            }
        )
    }

    inline fun <V> Flow<V>.observe(noinline collector: suspend (V) -> Unit) {
        lifecycleScope.launchWhenResumed {
            collect(FlowCollector(collector))
        }
    }

    fun <V> LiveData<V>.observe(observer: (V) -> Unit) {
        observe(viewLifecycleOwner, observer)
    }

    val Int.dp: Int
        get() = dp(requireContext())

    protected fun EditText.bindTo(liveData: MutableLiveData<String>) = bindTo(liveData, viewLifecycleOwner)

    protected inline fun <reified T> argument(key: String): T = requireArguments()[key] as T

    abstract fun initViews()

    abstract fun subscribe(viewModel: T)
}
