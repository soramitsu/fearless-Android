package jp.co.soramitsu.common.delegate

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.CompletableTransformer
import io.reactivex.SingleTransformer
import jp.co.soramitsu.common.interfaces.WithProgress
import javax.inject.Inject

class WithProgressImpl @Inject constructor() : WithProgress {

    private val progressVisibilityLiveData = MutableLiveData<Boolean>()

    override fun getProgressVisibility(): LiveData<Boolean> {
        return progressVisibilityLiveData
    }

    override fun <T> progressCompose(): SingleTransformer<T, T> {
        return SingleTransformer { single ->
            single.doOnSubscribe { progressVisibilityLiveData.postValue(true) }
                .doAfterTerminate { progressVisibilityLiveData.postValue(false) }
        }
    }

    override fun progressCompletableCompose(): CompletableTransformer {
        return CompletableTransformer { completable ->
            completable.doOnSubscribe { progressVisibilityLiveData.postValue(true) }
                .doAfterTerminate { progressVisibilityLiveData.postValue(false) }
        }
    }

    override fun showProgress() {
        progressVisibilityLiveData.postValue(true)
    }

    override fun hideProgress() {
        progressVisibilityLiveData.postValue(false)
    }
}