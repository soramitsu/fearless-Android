package jp.co.soramitsu.users.presentation.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_user_api.domain.interfaces.UserInteractor
import jp.co.soramitsu.feature_user_api.domain.model.User
import jp.co.soramitsu.users.UsersRouter

class UsersViewModel(
    private val interactor: UserInteractor,
    private val router: UsersRouter
) : BaseViewModel() {

    private val _usersLiveData = MutableLiveData<List<User>>()
    val usersLiveData: LiveData<List<User>> = _usersLiveData

    fun userClicked(user: User) {
        router.openUser(user.id)
    }

    fun getUsers() {
        disposables.add(
            interactor.getUsers()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread(), true)
                .subscribe({
                    _usersLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }
}