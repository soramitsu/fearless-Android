package jp.co.soramitsu.feature_user_api.domain.interfaces

import io.reactivex.Observable
import jp.co.soramitsu.feature_user_api.domain.model.User

interface UserInteractor {

    fun getUser(id: Int): Observable<User>

    fun getUsers(): Observable<List<User>>
}