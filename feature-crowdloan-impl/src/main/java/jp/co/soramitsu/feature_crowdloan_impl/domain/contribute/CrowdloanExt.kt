package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute

import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.Crowdloan
import java.math.BigInteger

fun mapFundInfoToCrowdloan(
    fundInfo: FundInfo,
    parachainMetadata: ParachainMetadata?,
    parachainId: BigInteger,
    currentBlockNumber: BlockNumber,
    expectedBlockTimeInMillis: BigInteger
) = Crowdloan(
    parachainMetadata =parachainMetadata,
    raised = fundInfo.raised,
    parachainId = parachainId,
    cap = fundInfo.cap,
    remainingTimeInMillis = expectedRemainingTime(currentBlockNumber, fundInfo.end, expectedBlockTimeInMillis),
    depositor = fundInfo.depositor
)

private fun expectedRemainingTime(
    currentBlock: BlockNumber,
    targetBlock: BlockNumber,
    expectedBlockTimeInMillis: BigInteger,
): Long {
    val blockDifference = targetBlock - currentBlock
    val expectedTimeDifference = blockDifference * expectedBlockTimeInMillis

    return expectedTimeDifference.toLong()
}

fun FundInfo.isActive(currentBlockNumber: BlockNumber) = end > currentBlockNumber
