package jp.co.soramitsu.feature_wallet_impl.data.buyToken.tx

interface DecoratableFunctions {

    val decorator: Decorator

    interface Decorator {
        fun function0(name: String): Function0

        fun <A1> function1(name: String): Function1<A1>
    }
}
