package jp.co.soramitsu.feature_wallet_impl.data.buyToken.query

interface DecoratableStorage {

    val decorator: Decorator

    interface Decorator {
        fun <R> plain(name: String, binder: (Any?) -> R): PlainStorageEntry<R>

        fun <K, R> single(name: String, binder: (Any?) -> R): SingleMapStorageEntry<K, R>
    }
}
