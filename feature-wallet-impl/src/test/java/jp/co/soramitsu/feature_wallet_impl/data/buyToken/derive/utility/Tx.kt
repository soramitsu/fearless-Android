package jp.co.soramitsu.feature_wallet_impl.data.buyToken.derive.utility

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.tx.DecoratableFunctions
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.tx.DecoratableTx
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.tx.Function1

interface UtilityFunctions : DecoratableFunctions

val DecoratableTx.utility: UtilityFunctions
    get() = decorate("Utility") {
        object : UtilityFunctions, DecoratableFunctions by this {}
    }

val UtilityFunctions.batch: Function1<List<GenericCall.Instance>>
    get() = decorator.function1("batch")
