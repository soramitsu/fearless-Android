package jp.co.soramitsu.users.presentation.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_user_api.domain.interfaces.UserInteractor
import jp.co.soramitsu.feature_user_api.domain.model.User
import jp.co.soramitsu.users.UsersRouter

class UserViewModel(
    private val interactor: UserInteractor,
    private val userId: Int,
    private val router: UsersRouter
) : BaseViewModel() {

    private val _userLiveData = MutableLiveData<User>()
    val userLiveData: LiveData<User> = _userLiveData

    init {
        disposables.add(
            interactor.getUser(userId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread(), true)
                .subscribe({
                    _userLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun backClicked() {
        router.returnToUsers()
    }
}