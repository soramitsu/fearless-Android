package jp.co.soramitsu.staking.impl.domain.common

import java.math.BigInteger
import jp.co.soramitsu.staking.api.domain.model.Nominations

fun Nominations.isWaiting(activeEraIndex: BigInteger): Boolean {
    return submittedInEra >= activeEraIndex
}
