package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.postprocessors

import jp.co.soramitsu.feature_staking_api.domain.model.ChildIdentity
import jp.co.soramitsu.feature_staking_api.domain.model.Identity
import jp.co.soramitsu.feature_staking_api.domain.model.RootIdentity
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationPostProcessor
import java.lang.IllegalArgumentException

private const val MAX_PER_CLUSTER = 3

object RemoveClusteringPostprocessor : RecommendationPostProcessor {

    override fun invoke(original: List<Validator>): List<Validator> {
        val clusterCounter = mutableMapOf<Identity, Int>()

        return original.filter { validator ->
            val clusterIdentity = validator.clusterIdentity()
            val currentCounter = clusterCounter.getOrDefault(clusterIdentity, 0)

            clusterCounter[clusterIdentity] = currentCounter + 1

            currentCounter < MAX_PER_CLUSTER
        }
    }

    private fun Validator.clusterIdentity() : Identity {
        return when(val validatorIdentity = identity) {
            is RootIdentity -> validatorIdentity
            is ChildIdentity -> validatorIdentity.parentIdentity
            else -> throw IllegalArgumentException("Unknown type of identity: ${validatorIdentity?.javaClass?.simpleName}")
        }
    }
}
