package jp.co.soramitsu.common.interfaces

import androidx.lifecycle.LiveData
import io.reactivex.CompletableTransformer
import io.reactivex.SingleTransformer

interface WithProgress {

    fun getProgressVisibility(): LiveData<Boolean>

    fun <T> progressCompose(): SingleTransformer<T, T>

    fun progressCompletableCompose(): CompletableTransformer

    fun showProgress()

    fun hideProgress()
}