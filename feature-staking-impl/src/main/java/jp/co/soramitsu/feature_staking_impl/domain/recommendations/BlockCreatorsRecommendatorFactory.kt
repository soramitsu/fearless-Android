package jp.co.soramitsu.feature_staking_impl.domain.recommendations

import androidx.lifecycle.Lifecycle

interface BlockCreatorsRecommendatorFactory<T> {
    suspend fun awaitBlockCreatorsLoading(lifecycle: Lifecycle)
    suspend fun create(lifecycle: Lifecycle): BlockCreatorRecommendator<T>
}
