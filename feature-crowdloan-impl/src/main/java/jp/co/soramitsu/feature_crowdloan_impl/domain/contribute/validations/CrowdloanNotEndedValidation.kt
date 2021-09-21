package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_impl.data.CrowdloanSharedState
import jp.co.soramitsu.feature_crowdloan_impl.domain.common.leaseIndexFromBlock
import jp.co.soramitsu.runtime.repository.ChainStateRepository

class CrowdloanNotEndedValidation(
    private val chainStateRepository: ChainStateRepository,
    private val crowdloanSharedState: CrowdloanSharedState,
    private val crowdloanRepository: CrowdloanRepository
) : ContributeValidation {

    override suspend fun validate(value: ContributeValidationPayload): ValidationStatus<ContributeValidationFailure> {
        val currentBlock = chainStateRepository.currentBlock(crowdloanSharedState.chainId())
        val blocksPerLease = crowdloanRepository.blocksPerLeasePeriod()

        val currentLeaseIndex = leaseIndexFromBlock(currentBlock, blocksPerLease)

        return when {
            currentBlock >= value.crowdloan.fundInfo.end -> crowdloanEndedFailure()
            currentLeaseIndex > value.crowdloan.fundInfo.firstSlot -> crowdloanEndedFailure()
            else -> ValidationStatus.Valid()
        }
    }

    private fun crowdloanEndedFailure(): ValidationStatus.NotValid<ContributeValidationFailure> =
        ValidationStatus.NotValid(DefaultFailureLevel.ERROR, ContributeValidationFailure.CrowdloanEnded)
}
