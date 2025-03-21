package jp.co.soramitsu.crowdloan.impl.domain.main

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.list.GroupedList
import jp.co.soramitsu.core.extrinsic.mortality.IChainStateRepository
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.Contribution
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.crowdloan.api.data.repository.CrowdloanRepository
import jp.co.soramitsu.crowdloan.api.data.repository.ParachainMetadata
import jp.co.soramitsu.crowdloan.api.data.repository.getContributions
import jp.co.soramitsu.crowdloan.impl.domain.contribute.mapFundInfoToCrowdloan
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAddress
import kotlin.reflect.KClass

class Crowdloan(
    val parachainMetadata: ParachainMetadata?,
    val parachainId: BigInteger,
    val raisedFraction: BigDecimal,
    val state: State,
    val leasePeriodInMillis: Long,
    val leasedUntilInMillis: Long,
    val fundInfo: FundInfo,
    val myContribution: Contribution?
) {

    sealed class State {

        companion object {

            val STATE_CLASS_COMPARATOR = Comparator<KClass<out State>> { first, _ ->
                when (first) {
                    Active::class -> -1
                    Finished::class -> 1
                    else -> 0
                }
            }
        }

        object Finished : State()

        class Active(val remainingTimeInMillis: Long) : State()
    }
}

typealias GroupedCrowdloans = GroupedList<KClass<out Crowdloan.State>, Crowdloan>

class CrowdloanInteractor(
    private val accountRepository: AccountRepository,
    private val crowdloanRepository: CrowdloanRepository,
    private val chainStateRepository: IChainStateRepository
) {

    fun crowdloansFlow(chain: Chain): Flow<GroupedCrowdloans> {
        return flow {
            val chainId = chain.id

            if (crowdloanRepository.isCrowdloansAvailable(chainId).not()) {
                emit(emptyMap())

                return@flow
            }

            val parachainMetadatas = runCatching {
                crowdloanRepository.getParachainMetadata(chain)
            }.getOrDefault(emptyMap())

            val metaAccount = accountRepository.getSelectedMetaAccount()

            val accountId = metaAccount.accountId(chain)!! // TODO ethereum

            val expectedBlockTime = chainStateRepository.expectedBlockTimeInMillis(chainId)
            val blocksPerLeasePeriod = crowdloanRepository.blocksPerLeasePeriod(chainId)
            val leaseOffset = crowdloanRepository.leaseOffset(chainId)

            val withBlockUpdates = chainStateRepository.currentBlockNumberFlow(chainId).map { currentBlockNumber ->
                val fundInfos = crowdloanRepository.allFundInfos(chainId)

                val contributionKeys = fundInfos.mapValues { (_, fundInfo) -> fundInfo.fundIndex }

                val contributions = crowdloanRepository.getContributions(chainId, accountId, contributionKeys)
                val winnerInfo = crowdloanRepository.getWinnerInfo(chainId, fundInfos)

                val aa = fundInfos.values
                    .map { fundInfo ->
                        val paraId = fundInfo.paraId
                        val minContribution = crowdloanRepository.minContribution(chain.id)

                        mapFundInfoToCrowdloan(
                            fundInfo = fundInfo,
                            parachainMetadata = parachainMetadatas[paraId],
                            parachainId = paraId,
                            currentBlockNumber = currentBlockNumber,
                            expectedBlockTimeInMillis = expectedBlockTime,
                            blocksPerLeasePeriod = blocksPerLeasePeriod,
                            leaseOffset = leaseOffset,
                            contribution = contributions[paraId],
                            hasWonAuction = winnerInfo.getValue(paraId),
                            minContribution = minContribution
                        )
                    }
                    .sortedWith(
                        compareByDescending<Crowdloan> { it.fundInfo.raised }
                            .thenBy { it.fundInfo.end }
                    )
                    .groupBy { it.state::class }
                    .toSortedMap(Crowdloan.State.STATE_CLASS_COMPARATOR)
                aa
            }

            emitAll(withBlockUpdates)
        }
    }

    suspend fun checkRemark(apiUrl: String, apiKey: String): Result<Boolean> = runCatching {
        val address = accountRepository.getSelectedMetaAccount().substrateAccountId?.toAddress(0.toShort())!!
        crowdloanRepository.checkRemark(apiUrl, apiKey, address)
    }
}
