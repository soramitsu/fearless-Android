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

//data class TokenFiatLocal(
//    @Embedded
//    val token: TokenLocal,
//    @Relation(parentColumn = "id", entityColumn = "tokenIdFiat")
//    val fiat: FiatTokenPriceLocal?,
//)
//
//data class BasicPoolWithTokenFiatLocal(
//    @Embedded
//    val basicPoolLocal: BasicPoolLocal,
//    @Relation(parentColumn = "tokenIdBase", entityColumn = "id", entity = TokenLocal::class)
//    val tokenBaseLocal: TokenFiatLocal,
//    @Relation(parentColumn = "tokenIdTarget", entityColumn = "id", entity = TokenLocal::class)
//    val tokenTargetLocal: TokenFiatLocal,
//)
