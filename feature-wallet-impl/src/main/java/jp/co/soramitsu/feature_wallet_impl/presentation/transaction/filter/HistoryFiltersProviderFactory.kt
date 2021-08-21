package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter

class HistoryFiltersProviderFactory {
    private var instance: HistoryFiltersProvider? = null

    @Synchronized
    fun get(): HistoryFiltersProvider {
        if(instance != null) return instance!!

        instance = HistoryFiltersProvider()

        return instance!!
    }
}
