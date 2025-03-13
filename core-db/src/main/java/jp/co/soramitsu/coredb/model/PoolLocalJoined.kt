package jp.co.soramitsu.coredb.model

import androidx.room.Embedded
import androidx.room.Relation

data class UserPoolJoinedLocal(
    @Embedded
    val userPoolLocal: UserPoolLocal,
    @Embedded
    val basicPoolLocal: BasicPoolLocal,
)

data class UserPoolJoinedLocalNullable(
    @Embedded
    val userPoolLocal: UserPoolLocal?,
    @Embedded
    val basicPoolLocal: BasicPoolLocal,
)
