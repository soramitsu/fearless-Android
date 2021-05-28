package jp.co.soramitsu.feature_wallet_api.data.mappers

import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Token

fun mapTokenTypeToTokenTypeLocal(type: Token.Type): TokenLocal.Type {
    return when (type) {
        Token.Type.DOT -> TokenLocal.Type.DOT
        Token.Type.KSM -> TokenLocal.Type.KSM
        Token.Type.WND -> TokenLocal.Type.WND
        Token.Type.ROC -> TokenLocal.Type.ROC
    }
}

fun mapTokenTypeLocalToTokenType(type: TokenLocal.Type): Token.Type {
    return when (type) {
        TokenLocal.Type.DOT -> Token.Type.DOT
        TokenLocal.Type.KSM -> Token.Type.KSM
        TokenLocal.Type.WND -> Token.Type.WND
        TokenLocal.Type.ROC -> Token.Type.ROC
    }
}

fun tokenTypeLocalFromNetworkType(networkType: Node.NetworkType): TokenLocal.Type {
    val tokenType = Token.Type.fromNetworkType(networkType)

    return mapTokenTypeToTokenTypeLocal(tokenType)
}
