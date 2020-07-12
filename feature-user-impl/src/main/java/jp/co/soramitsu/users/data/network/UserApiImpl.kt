package jp.co.soramitsu.users.data.network

import io.reactivex.Single
import jp.co.soramitsu.users.data.network.model.UserRemote
import javax.inject.Inject

class UserApiImpl @Inject constructor() : UserApi {

    override fun getUsers(): Single<List<UserRemote>> {
        return Single.just(mockUsers())
    }

    override fun getUser(id: Int): Single<UserRemote> {
        return Single.fromCallable {
            mockUsers().firstOrNull { it.id == id } ?: throw RuntimeException("")
        }
    }

    private fun mockUsers(): List<UserRemote> {
        return mutableListOf<UserRemote>().apply {
            add(UserRemote(1, "Василий", "Пупкин"))
            add(UserRemote(2, "Петр", "Петров"))
            add(UserRemote(3, "Александр", "Иванов"))
        }
    }
}