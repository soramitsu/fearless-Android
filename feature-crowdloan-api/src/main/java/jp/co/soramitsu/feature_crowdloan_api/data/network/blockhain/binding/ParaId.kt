package jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding

import java.math.BigInteger

typealias ParaId = BigInteger

fun ParaId.isMoonbeam(): Boolean {
    return this == 2002.toBigInteger()
}
