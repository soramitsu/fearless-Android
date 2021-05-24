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
    expectedBlockTimeInMillis: BigInteger,
    blocksPerLeasePeriod: BigInteger,
): Crowdloan {
    val leasePeriodInMillis = leasePeriodInMillis(blocksPerLeasePeriod, currentBlockNumber, fundInfo.lastSlot, expectedBlockTimeInMillis)

    val state = if (currentBlockNumber < fundInfo.end) {
        val remainingTime = expectedRemainingTime(currentBlockNumber, fundInfo.end, expectedBlockTimeInMillis)

        Crowdloan.State.Active(remainingTime)
    } else {
        Crowdloan.State.Finished
    }

    return Crowdloan(
        parachainMetadata =parachainMetadata,
        raised = fundInfo.raised,
        raisedFraction = fundInfo.raised.toBigDecimal() / fundInfo.cap.toBigDecimal(),
        parachainId = parachainId,
        cap = fundInfo.cap,
        leasePeriodInMillis = leasePeriodInMillis,
        leasedUntilInMillis = System.currentTimeMillis() + leasePeriodInMillis,
        state = state,
        depositor = fundInfo.depositor
    )
}

private fun leasePeriodInMillis(
    blocksPerLeasePeriod: BigInteger,
    currentBlockNumber: BigInteger,
    endingLeasePeriod: BigInteger,
    expectedBlockTimeInMillis: BigInteger
) : Long {
    val unlockedAtPeriod = endingLeasePeriod + BigInteger.ONE // next period after end one
    val unlockedAtBlock = blocksPerLeasePeriod * unlockedAtPeriod

    return expectedRemainingTime(
        currentBlockNumber,
        unlockedAtBlock,
        expectedBlockTimeInMillis
    )
}

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
