package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute

import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import jp.co.soramitsu.feature_crowdloan_impl.data.network.blockhain.extrinsic.contribute
import jp.co.soramitsu.feature_crowdloan_impl.data.repository.ChainStateRepository
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.Crowdloan
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import java.math.BigDecimal

typealias AdditionalOnChainSubmission = suspend ExtrinsicBuilder.() -> Unit

class CrowdloanContributeInteractor(
    private val extrinsicService: ExtrinsicService,
    private val feeEstimator: FeeEstimator,
    private val accountRepository: AccountRepository,
    private val chainStateRepository: ChainStateRepository,
    private val crowdloanRepository: CrowdloanRepository
) {

    fun crowdloanStateFlow(
        parachainId: ParaId,
        parachainMetadata: ParachainMetadata? = null
    ): Flow<Crowdloan> = accountRepository.selectedNetworkTypeFlow().flatMapLatest {
        val expectedBlockTime = chainStateRepository.expectedBlockTimeInMillis()
        val blocksPerLeasePeriod = crowdloanRepository.blocksPerLeasePeriod()

        combine(
            crowdloanRepository.fundInfoFlow(parachainId, it),
            chainStateRepository.currentBlockNumberFlow(it)
        ) { fundInfo, blockNumber ->
            mapFundInfoToCrowdloan(
                fundInfo = fundInfo,
                parachainMetadata = parachainMetadata,
                parachainId = parachainId,
                currentBlockNumber = blockNumber,
                expectedBlockTimeInMillis = expectedBlockTime,
                blocksPerLeasePeriod = blocksPerLeasePeriod
            )
        }
    }

    suspend fun estimateFee(
        parachainId: ParaId,
        contribution: BigDecimal,
        token: Token,
        additional: AdditionalOnChainSubmission?
    ) = withContext(Dispatchers.Default) {
        val contributionInPlanks = token.planksFromAmount(contribution)

        val feeInPlanks = feeEstimator.estimateFee(accountRepository.getSelectedAccount().address) {
            contribute(parachainId, contributionInPlanks)

            additional?.invoke(this)
        }

        token.amountFromPlanks(feeInPlanks)
    }

    suspend fun contribute(
        originAddress: String,
        parachainId: ParaId,
        contribution: BigDecimal,
        token: Token,
        additional: AdditionalOnChainSubmission?
    ) = withContext(Dispatchers.Default) {
        val contributionInPlanks = token.planksFromAmount(contribution)

        extrinsicService.submitExtrinsic(originAddress) {
            contribute(parachainId, contributionInPlanks)

            additional?.invoke(this)
        }.getOrThrow()
    }
}
