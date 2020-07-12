package jp.co.soramitsu.users.data.network

import io.reactivex.Single
import jp.co.soramitsu.users.data.network.model.UserRemote

interface UserApi {

    fun getUsers(): Single<List<UserRemote>>

    fun getUser(id: Int): Single<UserRemote>
}