package jp.co.soramitsu.common.base

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.EventObserver
import javax.inject.Inject

abstract class BaseFragment<T : BaseViewModel> : Fragment() {

    @Inject protected open lateinit var viewModel: T

    private val observables = mutableListOf<LiveData<*>>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        inject()
        initViews()
        subscribe(viewModel)

        observe(viewModel.errorLiveData, EventObserver {
            showError(it)
        })

        observe(viewModel.errorWithTitleLiveData, EventObserver {
            showErrorWithTitle(it.first, it.second)
        })

        observe(viewModel.errorFromResourceLiveData, EventObserver {
            showErrorFromResponse(it)
        })
    }

    protected fun showError(errorMessage: String) {
        AlertDialog.Builder(activity!!)
            .setTitle(R.string.common_error_general_title)
            .setMessage(errorMessage)
            .setPositiveButton(R.string.common_ok) { _, _ -> }
            .show()
    }

    protected fun showErrorFromResponse(resId: Int) {
        AlertDialog.Builder(activity!!)
            .setTitle(R.string.common_error_general_title)
            .setMessage(resId)
            .setPositiveButton(R.string.common_ok) { _, _ -> }
            .show()
    }

    protected fun showErrorWithTitle(title: String, errorMessage: String) {
        AlertDialog.Builder(activity!!)
            .setTitle(title)
            .setMessage(errorMessage)
            .setPositiveButton(R.string.common_ok) { _, _ -> }
            .show()
    }

    override fun onDestroyView() {
        observables.forEach { it.removeObservers(this) }
        super.onDestroyView()
    }

    @Suppress("unchecked_cast")
    protected fun <V : Any?> observe(source: LiveData<V>, observer: Observer<V>) {
        source.observe(this, observer as Observer<in Any?>)
        observables.add(source)
    }

    abstract fun initViews()

    abstract fun inject()

    abstract fun subscribe(viewModel: T)
}