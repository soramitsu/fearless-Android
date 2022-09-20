package jp.co.soramitsu.common.data.network.runtime.model

import java.math.BigInteger

// todo there were a field which caused an errors:
// val weight: Long
// we weren't use it anywhere so I just removed it
// New response should have a struct like this:
// {
//      "weight":{"ref_time":164143000},
//      "class":"normal",
//      "partialFee":"15407544760"
// }
class FeeResponse(
    val partialFee: BigInteger
)
