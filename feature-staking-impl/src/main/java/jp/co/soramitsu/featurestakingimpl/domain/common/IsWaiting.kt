package jp.co.soramitsu.featurestakingimpl.domain.common

import java.math.BigInteger
import jp.co.soramitsu.featurestakingapi.domain.model.Nominations

fun Nominations.isWaiting(activeEraIndex: BigInteger): Boolean {
    return submittedInEra >= activeEraIndex
}
