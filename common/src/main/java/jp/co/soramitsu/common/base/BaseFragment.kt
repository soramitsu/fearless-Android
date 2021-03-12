package jp.co.soramitsu.common.base

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.common.utils.bindTo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

abstract class BaseFragment<T : BaseViewModel> : Fragment() {

    @Inject protected open lateinit var viewModel: T

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        inject()
        initViews()
        subscribe(viewModel)

        viewModel.errorLiveData.observeEvent(::showError)

        viewModel.errorWithTitleLiveData.observeEvent {
            showErrorWithTitle(it.first, it.second)
        }

        viewModel.messageLiveData.observeEvent(::showMessage)
    }

    protected fun showError(errorMessage: String) {
        buildErrorDialog(getString(R.string.common_error_general_title), errorMessage)
            .show()
    }

    protected fun showErrorWithTitle(title: String, errorMessage: String) {
        buildErrorDialog(title, errorMessage).show()
    }

    protected open fun buildErrorDialog(title: String, errorMessage: String): AlertDialog {
        return AlertDialog.Builder(themedContext())
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

    inline fun <V> Flow<V>.observe(crossinline collector: suspend (V) -> Unit) {
        lifecycleScope.launchWhenResumed {
            collect(collector)
        }
    }

    fun <V> LiveData<V>.observe(observer: (V) -> Unit) {
        observe(viewLifecycleOwner, observer)
    }

    protected fun EditText.bindTo(liveData: MutableLiveData<String>) = bindTo(liveData, viewLifecycleOwner)

    protected inline fun <reified T> argument(key: String): T = arguments!![key] as T

    protected fun themedContext(): Context {
        return view!!.context
    }

    abstract fun initViews()

    abstract fun inject()

    abstract fun subscribe(viewModel: T)
}
