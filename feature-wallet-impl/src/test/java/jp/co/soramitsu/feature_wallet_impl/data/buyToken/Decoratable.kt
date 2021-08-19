package jp.co.soramitsu.feature_wallet_impl.data.buyToken

abstract class Decoratable {
    private val initialized = mutableMapOf<String, Any?>()

    @Suppress("UNCHECKED_CAST")
    protected fun <R> decorateInternal(key: String, lazyCreate: () -> R): R {
        return initialized.getOrPut(key) {
            lazyCreate()
        } as R
    }
}
