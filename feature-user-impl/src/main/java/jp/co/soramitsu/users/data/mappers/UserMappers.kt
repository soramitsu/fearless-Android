package jp.co.soramitsu.users.data.mappers

import jp.co.soramitsu.core_db.model.UserLocal
import jp.co.soramitsu.feature_user_api.domain.model.User
import jp.co.soramitsu.users.data.network.model.UserRemote

fun mapUserToUserLocal(user: User): UserLocal {
    return with(user) {
        UserLocal(id, firstName, lastName)
    }
}

fun mapUserLocalToUser(user: UserLocal): User {
    return with(user) {
        User(id, firstName, lastName)
    }
}

fun mapUserRemoteToUser(user: UserRemote): User {
    return with(user) {
        User(id, firstName, lastName)
    }
}