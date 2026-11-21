package jp.co.soramitsu.staking.impl.presentation.common

import java.io.File
import jp.co.soramitsu.common.feedback.TransferSeal
import jp.co.soramitsu.staking.impl.domain.payout.PayoutInteractor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seal (brand kit SPEC v4 part 3) on the relay-chain staking confirmations: which real event lights which stage,
 * and that every confirmation's hand-over goes through it. The payout and pool-join view models are also covered end
 * to end (ConfirmPayoutViewModelTest, ConfirmJoinPoolSubmissionTest).
 */
class StakingSealTest {
    @After fun clear() = TransferSeal.clear()

    @Test fun `the hand-over lights ready over the screen, and the hash lights the rest together, returned at once`() = runBlocking {
        var whileRunning: TransferSeal.Record? = null
        val result = StakingSeal.submit { whileRunning = TransferSeal.record.value; Result.success("0xhash") }
        assertEquals(TransferSeal.Stage.Ready, whileRunning?.stage)
        assertTrue("drawn over the screen: these confirmations show a spinner in their button", whileRunning!!.shownOver)
        assertEquals(Result.success("0xhash"), result)
        val record = TransferSeal.record.value
        assertEquals(TransferSeal.Stage.Accepted, record.stage)
        assertEquals(record.acceptedAtMs, record.signedAtMs)
        assertEquals(record.acceptedAtMs, record.sentAtMs)
    }

    @Test fun `a refused operation breaks the seal`() = runBlocking {
        StakingSeal.submit<Unit> { Result.failure(IllegalStateException("refused")) }
        assertEquals(TransferSeal.Stage.Failed, TransferSeal.record.value.stage)
    }

    @Test fun `a cancelled or thrown hand-over clears the seal`() = runBlocking {
        StakingSeal.submit<Unit> { Result.failure(kotlinx.coroutines.CancellationException("left")) }
        assertEquals(TransferSeal.Stage.Idle, TransferSeal.record.value.stage)
        runCatching { StakingSeal.submit<Unit> { throw IllegalStateException("threw") } }
        assertEquals(TransferSeal.Stage.Idle, TransferSeal.record.value.stage)
    }

    @Test fun `a payout review refused before signing clears the seal, any other payout failure breaks it`() = runBlocking {
        StakingSeal.payout { Result.failure(IllegalStateException(PayoutInteractor.STALE_REVIEW)) }
        assertEquals(TransferSeal.Stage.Idle, TransferSeal.record.value.stage)
        StakingSeal.payout { Result.failure(IllegalStateException("refused by the node")) }
        assertEquals(TransferSeal.Stage.Failed, TransferSeal.record.value.stage)
        StakingSeal.payout { Result.success("0xhash") }
        assertEquals(TransferSeal.Stage.Accepted, TransferSeal.record.value.stage)
    }

    @Test fun `an unbond result for an attempt no longer current clears the seal, a current refusal breaks it`() = runBlocking {
        StakingSeal.unbond(isCurrent = { false }) { Result.failure<String>(IllegalStateException("stale")) }
        assertEquals(TransferSeal.Stage.Idle, TransferSeal.record.value.stage)
        StakingSeal.unbond(isCurrent = { true }) { Result.failure<String>(IllegalStateException("refused")) }
        assertEquals(TransferSeal.Stage.Failed, TransferSeal.record.value.stage)
    }

    /**
     * Wiring: each confirmation's sendTransaction hands its one signing call to the seal (after its validation, which
     * runs before sendTransaction), and lights nothing anywhere else.
     */
    @Test fun `every relay-chain staking confirmation submits through the seal`() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "design/fw.tokens.json").isFile }
        val base = File(root, "feature-staking-impl/src/main/java/jp/co/soramitsu/staking/impl/presentation")
        val expected = mapOf(
            "confirm/ConfirmStakingViewModel.kt" to listOf("StakingSeal.submit {", "StakingSeal.submit { setupStakingInteractor.setupStaking(collator"),
            "staking/bond/confirm/ConfirmBondMoreViewModel.kt" to listOf("StakingSeal.submit {\n            bondMoreInteractor.bondMore("),
            "staking/bond/select/SelectBondMoreViewModel.kt" to listOf("StakingSeal.submit {\n            bondMoreInteractor.bondMore("),
            "staking/unbond/confirm/ConfirmUnbondViewModel.kt" to listOf("StakingSeal.unbond(attempt::isCurrent) {\n                unbondInteractor.unbond("),
            "staking/unbond/select/SelectUnbondViewModel.kt" to listOf("StakingSeal.unbond(attempt::isCurrent) {\n                unbondInteractor.unbond("),
            "staking/rebond/confirm/ConfirmRebondViewModel.kt" to listOf("StakingSeal.submit {\n            rebondInteractor.rebond("),
            "staking/redeem/RedeemViewModel.kt" to listOf("StakingSeal.submit {\n            redeemInteractor.redeem("),
            "payouts/confirm/ConfirmPayoutViewModel.kt" to listOf("StakingSeal.payout { payoutInteractor.makePayouts(payload) }"),
            "staking/rewardDestination/confirm/ConfirmRewardDestinationViewModel.kt" to listOf("StakingSeal.submit { rewardDestinationInteractor.changeRewardDestination("),
            "staking/controller/confirm/ConfirmSetControllerViewModel.kt" to listOf("StakingSeal.submit {\n            controllerInteractor.setController(")
        )
        for ((path, needles) in expected) {
            val source = File(base, path).readText()
            val send = source.substring(source.indexOf("private fun sendTransaction"))
            for (needle in needles) assertTrue("$path hands its signing call to the seal", send.contains(needle))
            assertTrue("$path lights nothing outside the hand-over", !source.substringBefore("private fun sendTransaction").contains("StakingSeal."))
        }
    }
}
