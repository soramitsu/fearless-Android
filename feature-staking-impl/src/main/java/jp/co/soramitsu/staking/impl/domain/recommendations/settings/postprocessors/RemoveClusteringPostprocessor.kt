package jp.co.soramitsu.staking.impl.domain.recommendations.settings.postprocessors

import jp.co.soramitsu.staking.api.domain.model.ChildIdentity
import jp.co.soramitsu.staking.api.domain.model.Identity
import jp.co.soramitsu.staking.api.domain.model.RootIdentity
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.RecommendationPostProcessor

private const val MAX_PER_CLUSTER = 2

object RemoveClusteringPostprocessor : RecommendationPostProcessor<Validator> {

    override fun invoke(original: List<Validator>): List<Validator> {
        val clusterCounter = mutableMapOf<Identity, Int>()

        return original.filter { validator ->
            validator.clusterIdentity()?.let {
                val currentCounter = clusterCounter.getOrDefault(it, 0)

                clusterCounter[it] = currentCounter + 1

                currentCounter < MAX_PER_CLUSTER
            } ?: true
        }
    }

    private fun Validator.clusterIdentity(): Identity? {
        return when (val validatorIdentity = identity) {
            is RootIdentity -> validatorIdentity
            is ChildIdentity -> validatorIdentity.parentIdentity
            else -> null
        }
    }
}
