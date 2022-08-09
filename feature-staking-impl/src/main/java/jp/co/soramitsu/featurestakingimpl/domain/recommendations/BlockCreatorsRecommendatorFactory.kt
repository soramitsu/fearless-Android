package jp.co.soramitsu.featurestakingimpl.domain.recommendations

import androidx.lifecycle.Lifecycle

interface BlockCreatorsRecommendatorFactory<T> {
    suspend fun awaitBlockCreatorsLoading(lifecycle: Lifecycle)
    suspend fun create(lifecycle: Lifecycle): BlockCreatorRecommendator<T>
}
