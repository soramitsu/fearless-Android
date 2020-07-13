package jp.co.soramitsu.users.domain

import io.reactivex.Observable
import jp.co.soramitsu.feature_user_api.domain.interfaces.UserInteractor
import jp.co.soramitsu.feature_user_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_user_api.domain.model.User
import javax.inject.Inject

class UsersInteractorImpl @Inject constructor(
    private val userRepository: UserRepository
) : UserInteractor {

    override fun getUsers(): Observable<List<User>> {
        return userRepository.getUsers()
    }

    override fun getUser(id: Int): Observable<User> {
        return userRepository.getUser(id)
    }
}