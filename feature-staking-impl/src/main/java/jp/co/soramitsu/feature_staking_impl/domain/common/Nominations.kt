package jp.co.soramitsu.feature_staking_impl.domain.common

import jp.co.soramitsu.feature_staking_api.domain.model.Nominations
import java.math.BigInteger

fun Nominations.isWaiting(activeEraIndex: BigInteger): Boolean {
    return submittedInEra == activeEraIndex
}
