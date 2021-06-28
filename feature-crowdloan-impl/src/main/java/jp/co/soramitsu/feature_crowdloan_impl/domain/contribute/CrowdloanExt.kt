package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute

import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.Contribution
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import jp.co.soramitsu.feature_crowdloan_impl.domain.common.leaseIndexFromBlock
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.Crowdloan
import java.math.BigInteger
import java.math.MathContext

fun mapFundInfoToCrowdloan(
    fundInfo: FundInfo,
    parachainMetadata: ParachainMetadata?,
    parachainId: BigInteger,
    currentBlockNumber: BlockNumber,
    expectedBlockTimeInMillis: BigInteger,
    blocksPerLeasePeriod: BigInteger,
    contribution: Contribution?,
    hasWonAuction: Boolean
): Crowdloan {
    val leasePeriodInMillis = leasePeriodInMillis(blocksPerLeasePeriod, currentBlockNumber, fundInfo.lastSlot, expectedBlockTimeInMillis)

    val state = if (isCrowdloanActive(fundInfo, currentBlockNumber, blocksPerLeasePeriod, hasWonAuction)) {
        val remainingTime = expectedRemainingTime(currentBlockNumber, fundInfo.end, expectedBlockTimeInMillis)

        Crowdloan.State.Active(remainingTime)
    } else {
        Crowdloan.State.Finished
    }

    return Crowdloan(
        parachainMetadata = parachainMetadata,
        raisedFraction = fundInfo.raised.toBigDecimal().divide(fundInfo.cap.toBigDecimal(), MathContext.DECIMAL32),
        parachainId = parachainId,
        leasePeriodInMillis = leasePeriodInMillis,
        leasedUntilInMillis = System.currentTimeMillis() + leasePeriodInMillis,
        state = state,
        fundInfo = fundInfo,
        myContribution = contribution
    )
}

private fun isCrowdloanActive(
    fundInfo: FundInfo,
    currentBlockNumber: BigInteger,
    blocksPerLeasePeriod: BigInteger,
    hasWonAuction: Boolean,
): Boolean {
    return currentBlockNumber < fundInfo.end && // crowdloan is not ended
        // first slot is not yet passed
        leaseIndexFromBlock(currentBlockNumber, blocksPerLeasePeriod) <= fundInfo.firstSlot &&
        // cap is not reached
        fundInfo.raised < fundInfo.cap &&
        // crowdloan considered closed if parachain already won auction
        !hasWonAuction
}

private fun leasePeriodInMillis(
    blocksPerLeasePeriod: BigInteger,
    currentBlockNumber: BigInteger,
    endingLeasePeriod: BigInteger,
    expectedBlockTimeInMillis: BigInteger
): Long {
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
