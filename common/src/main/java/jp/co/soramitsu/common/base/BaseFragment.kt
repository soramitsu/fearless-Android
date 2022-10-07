package jp.co.soramitsu.common.base

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector

abstract class BaseFragment<T : BaseViewModel> : Fragment {

    abstract val viewModel: T

    constructor(contentLayoutId: Int) : super(contentLayoutId)

    constructor() : super()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews()
        subscribe(viewModel)

        viewModel.errorLiveData.observeEvent(::showError)

        viewModel.errorWithTitleLiveData.observeEvent {
            showErrorWithTitle(it.first, it.second)
        }

        viewModel.messageLiveData.observeEvent(::showMessage)
    }

    protected inline fun onBackPressed(crossinline action: () -> Unit) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                action()
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    protected fun showError(errorMessage: String) {
        buildErrorDialog(getString(R.string.common_error_general_title), errorMessage)
            .show()
    }

    protected fun showErrorWithTitle(title: String, errorMessage: String) {
        buildErrorDialog(title, errorMessage).show()
    }

    protected open fun buildErrorDialog(title: String, errorMessage: String): AlertDialog {
        return AlertDialog.Builder(ContextThemeWrapper(context, R.style.WhiteOverlay))
            .setTitle(title)
            .setMessage(errorMessage)
            .setPositiveButton(R.string.common_ok) { _, _ -> }
            .create()
    }

    protected fun showMessage(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT)
            .show()
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

    inline fun <reified T : ViewState> LiveData<ViewState>.observeState(crossinline observer: (T) -> Unit) {
        observe(viewLifecycleOwner) {
            if (it is T) {
                observer(it)
            }
        }
    }

    val Int.dp: Int
        get() = dp(requireContext())

    protected fun EditText.bindTo(liveData: MutableLiveData<String>) = bindTo(liveData, viewLifecycleOwner)

    protected inline fun <reified T> argument(key: String): T = requireArguments()[key] as T

    abstract fun initViews()

    abstract fun subscribe(viewModel: T)
}

interface ViewState {
    object Empty : ViewState
}
