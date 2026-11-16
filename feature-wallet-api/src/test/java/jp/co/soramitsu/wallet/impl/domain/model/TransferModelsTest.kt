package jp.co.soramitsu.wallet.impl.domain.model

import jp.co.soramitsu.core.models.ChainAssetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

@Suppress("DEPRECATION")
class TransferModelsTest {
    private val dot = CoreAsset(
        id = "dot",
        name = "Polkadot",
        symbol = "DOT",
        iconUrl = "icon",
        chainId = "polkadot",
        chainName = "Polkadot",
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = 10,
        staking = CoreAsset.StakingType.UNSUPPORTED,
        purchaseProviders = null,
        supportStakingPool = false,
        isUtility = true,
        type = ChainAssetType.Normal,
        currencyId = null,
        existentialDeposit = null,
        color = null,
        isNative = true
    )

    @Test
    fun transferConvertsItsAmountAndFeeToPlanks() {
        val transfer = Transfer("sender", "recipient", BigDecimal("1.5"), dot, estimateFee = BigDecimal("0.01"))

        assertEquals(BigInteger("15000000000"), transfer.amountInPlanks)
        assertEquals(BigInteger("100000000"), transfer.estimateFeeInPlanks)
        assertNull(transfer.copy(estimateFee = null).estimateFeeInPlanks)
        assertNull(transfer.additionalParams)
        assertNull(transfer.quoteId)
        assertEquals(false, transfer.sendMax)
    }

    @Test
    fun transferValidityCoversEveryOutcome() {
        val transfer = Transfer("sender", "recipient", BigDecimal("10"), dot)

        assertSame(TransferValidityLevel.Error.Status.NotEnoughFunds, transfer.check(transferable = "10.5", fee = "1"))
        assertSame(TransferValidityLevel.Error.Status.DeadRecipient, transfer.check(recipient = "0", existentialDeposit = "11"))
        assertSame(TransferValidityLevel.Warning.Status.WillRemoveAccount, transfer.check(total = "11", existentialDeposit = "1.5"))
        assertSame(
            TransferValidityLevel.Warning.Status.WillRemoveAccount,
            transfer.check(isUtility = false, utilityBalance = "1", fee = "0.5", utilityExistentialDeposit = "0.6")
        )
        assertSame(TransferValidityLevel.Ok, transfer.check(isUtility = false, utilityBalance = "5", fee = "0.5", tip = "0.5"))
        assertSame(TransferValidityLevel.Ok, transfer.check())
        assertEquals(TransferValidityLevel.Error, TransferValidityLevel.Error.Status.NotEnoughFunds.level)
        assertTrue(TransferValidityLevel.Error > TransferValidityLevel.Warning)
        assertTrue(TransferValidityLevel.Ok < TransferValidityLevel.Warning)
    }

    @Test
    fun transferParamsCarryTheirExtras() {
        val cbdc = CBDCTransferParams("memo", BigInteger.TEN, BigInteger.ONE, BigInteger.TWO)
        val ton = TonTransferParams("hello")
        val substrate = SubstrateTransferParams(null, BigInteger.ONE)

        assertEquals("memo", cbdc.comment)
        assertEquals(BigInteger.TEN, cbdc.maxAmountInPlanks)
        assertEquals(BigInteger.ONE, cbdc.tipInPlanks)
        assertEquals(BigInteger.TWO, cbdc.appId)
        assertEquals("hello", ton.comment)
        assertNull(substrate.tipInPlanks)
        assertEquals(BigInteger.ONE, substrate.appId)
        val transfer = Transfer("sender", "recipient", BigDecimal.ONE, dot, additionalParams = ton, quoteId = "q", sendMax = true)
        assertSame(ton, transfer.additionalParams)
        assertEquals("q", transfer.quoteId)
        assertTrue(transfer.sendMax)
    }

    @Test
    fun crossChainAmountIncludesTheDestinationFeeExactly() {
        val transfer = CrossChainTransfer("polkadot", "assethub", "recipient", BigDecimal("1.25"), BigDecimal("0.0000000001"), dot)

        assertEquals(BigInteger("12500000001"), transfer.fullAmountInPlanks)
        assertEquals(transfer.fullAmountInPlanks, transfer.exactFullAmountInPlanks())
        assertEquals("polkadot", transfer.originChainId)
        assertEquals("assethub", transfer.destinationChainId)
        assertEquals("recipient", transfer.recipient)
    }

    @Test
    fun crossChainAmountRefusesWhatCannotBeSubmitted() {
        val failures = listOf(
            CrossChainTransfer("a", "b", "r", BigDecimal.ZERO, BigDecimal.ZERO, dot),
            CrossChainTransfer("a", "b", "r", BigDecimal.ONE, BigDecimal("-0.1"), dot),
            CrossChainTransfer("a", "b", "r", BigDecimal.ONE, BigDecimal.ZERO, dot.copy(precision = -1)),
            CrossChainTransfer("a", "b", "r", BigDecimal("0.00000000001"), BigDecimal.ZERO, dot)
        ).map { runCatching { it.exactFullAmountInPlanks() }.exceptionOrNull() }

        assertEquals("XCM amount must be greater than zero", failures[0]?.message)
        assertEquals("XCM destination fee must not be negative", failures[1]?.message)
        assertEquals("XCM asset precision must not be negative", failures[2]?.message)
        assertTrue(failures[3] is ArithmeticException)
    }

    @Test
    fun crossChainValidityCoversEveryOutcome() {
        val transfer = CrossChainTransfer("a", "b", "r", BigDecimal("10"), BigDecimal.ZERO, dot)

        assertSame(TransferValidityLevel.Error.Status.NotEnoughFunds, transfer.check(transferable = "10.5", fee = "1"))
        assertSame(TransferValidityLevel.Error.Status.DeadRecipient, transfer.check(recipient = "0", existentialDeposit = "11"))
        assertSame(TransferValidityLevel.Warning.Status.WillRemoveAccount, transfer.check(total = "11", existentialDeposit = "1.5"))
        assertSame(
            TransferValidityLevel.Warning.Status.WillRemoveAccount,
            transfer.check(isUtility = false, utilityBalance = "1", fee = "0.5", utilityExistentialDeposit = "0.6")
        )
        assertSame(TransferValidityLevel.Ok, transfer.check(tip = "0.5"))
    }

    @Test
    fun operationTypesKeepTheirDetails() {
        val extrinsic = Operation.Type.Extrinsic("0x1", "balances", "transferKeepAlive", BigInteger.ONE, Operation.Status.COMPLETED)
        val reward = Operation.Type.Reward(BigInteger.TEN, isReward = false, era = 12, validator = null)
        val transfer = Operation.Type.Transfer(null, "me", BigInteger.TWO, "you", "me", Operation.Status.PENDING, null)
        val swap = Operation.Type.Swap("0x2", "polkaswap", BigInteger.ONE, BigInteger.ZERO, null, dot, null, BigInteger.ONE, Operation.Status.UNKNOWN)
        val operation = Operation("id", "address", extrinsic, 1_000, dot)

        assertEquals("Transfer Keep Alive", extrinsic.formatted(extrinsic.call))
        assertEquals("Balances", reward.formatted("balances"))
        assertEquals(12, reward.era)
        assertNull(transfer.hash)
        assertSame(dot, swap.targetAsset)
        assertEquals(operation, operation.copy())
        assertNotEquals(operation, operation.copy(type = transfer))
        assertEquals(Operation.Status.COMPLETED, Operation.Status.fromSuccess(true))
        assertEquals(Operation.Status.FAILED, Operation.Status.fromSuccess(false))
        assertEquals(4, Operation.Status.values().size)
    }

    private fun Transfer.check(
        transferable: String = "100",
        total: String = "100",
        fee: String = "0.1",
        recipient: String = "5",
        existentialDeposit: String = "1",
        isUtility: Boolean = true,
        utilityBalance: String = "100",
        utilityExistentialDeposit: String = "1",
        tip: String? = null
    ) = validityStatus(
        BigDecimal(transferable), BigDecimal(total), BigDecimal(fee), BigDecimal(recipient), BigDecimal(existentialDeposit),
        isUtility, BigDecimal(utilityBalance), BigDecimal(utilityExistentialDeposit), tip?.let(::BigDecimal)
    )

    private fun CrossChainTransfer.check(
        transferable: String = "100",
        total: String = "100",
        fee: String = "0.1",
        recipient: String = "5",
        existentialDeposit: String = "1",
        isUtility: Boolean = true,
        utilityBalance: String = "100",
        utilityExistentialDeposit: String = "1",
        tip: String? = null
    ) = validityStatus(
        BigDecimal(transferable), BigDecimal(total), BigDecimal(fee), BigDecimal(recipient), BigDecimal(existentialDeposit),
        isUtility, BigDecimal(utilityBalance), BigDecimal(utilityExistentialDeposit), tip?.let(::BigDecimal)
    )
}
