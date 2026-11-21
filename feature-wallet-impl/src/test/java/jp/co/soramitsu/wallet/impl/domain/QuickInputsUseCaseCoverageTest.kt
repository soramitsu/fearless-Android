package jp.co.soramitsu.wallet.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.utils.TON_BASE_FORWARD_AMOUNT
import jp.co.soramitsu.common.utils.initializeTonAddressCode
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.TonTransferParams
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quick amounts with known balances: swap fees, cross-chain fees, non-utility transfers and TON forward amounts. */
class QuickInputsUseCaseCoverageTest {

    private val xor = WalletTestModels.asset(id = "xor", chainId = SORA, precision = 18, currencyId = XOR_ID)
    private val vol = WalletTestModels.asset(
        id = "val", symbol = "VAL", chainId = SORA, precision = 18, currencyId = VAL_ID, isUtility = false
    )
    private val sora = WalletTestModels.chain(id = SORA, assets = listOf(xor, vol))

    private val dot = WalletTestModels.asset(chainId = POLKADOT)
    private val usdt = WalletTestModels.asset(id = "usdt", chainId = POLKADOT, precision = 6, isUtility = false)
    private val polkadot = WalletTestModels.chain(id = POLKADOT, assets = listOf(dot, usdt))

    private val tonCoin = WalletTestModels.asset(id = "ton", chainId = TON, precision = 9)
    private val ton = WalletTestModels.chain(id = TON, assets = listOf(tonCoin), ecosystem = Ecosystem.Ton)

    private val repository = mockk<WalletRepository>()
    private val accounts = mockk<AccountRepository>()
    private val chains = mockk<ChainsRepository>()
    private val constants = mockk<WalletConstants>()
    private val existentialDeposit = mockk<ExistentialDepositUseCase>()
    private val xcm = mockk<XcmInteractor>()
    private val swap = mockk<PolkaswapInteractor>()
    private val useCase = QuickInputsUseCaseImpl(
        repository, accounts, chains, constants, existentialDeposit, xcm, swap, Dispatchers.Unconfined
    )

    init {
        coEvery { chains.getChain(SORA) } returns sora
        coEvery { chains.getChain(POLKADOT) } returns polkadot
        coEvery { chains.getChain(TON) } returns ton
        coEvery { accounts.getSelectedMetaAccount() } returns WalletTestModels.metaAccount(ton = ByteArray(32))
        coEvery { constants.tip(any()) } returns null
        every { swap.polkaswapChainId } returns SORA
        coEvery { repository.getAsset(any(), any(), xor, null) } returns WalletTestModels.fundedAsset(xor)
        coEvery { repository.getAsset(any(), any(), vol, null) } returns WalletTestModels.fundedAsset(vol)
        coEvery { repository.getAsset(any(), any(), dot, null) } returns WalletTestModels.fundedAsset(dot)
        coEvery { repository.getAsset(any(), any(), usdt, null) } returns WalletTestModels.fundedAsset(usdt)
        coEvery { repository.getAsset(any(), any(), tonCoin, null) } returns WalletTestModels.fundedAsset(tonCoin, "10")
    }

    @Test
    fun `a swap from the utility asset keeps its swap fee`() = runTest {
        coEvery {
            swap.estimateSwapFee(1, XOR_ID, VAL_ID, any(), Market.SMART, WithDesired.INPUT)
        } returns BigInteger.TEN.pow(18)

        val amounts = useCase.calculatePolkaswapQuickInputs(xor.id, vol.id)

        assertAmounts(amounts, "74", "49", "24")
    }

    @Test
    fun `a swap from another asset offers its share of the balance`() = runTest {
        val amounts = useCase.calculatePolkaswapQuickInputs(vol.id, xor.id)

        assertAmounts(amounts, "75", "50", "25")
        coVerify(exactly = 0) { swap.estimateSwapFee(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a cross-chain utility transfer keeps the minimum, the destination fee and the origin fee`() = runTest {
        stubCrossChain()

        val amounts = useCase.calculateXcmTransfersQuickInputs(POLKADOT, DESTINATION, dot.id)

        // 100 DOT less 1.1 x the 1 DOT minimum leaves 98.9 DOT; each share pays 0.1 + 0.01 DOT of fees.
        assertAmounts(amounts, "74.065", "49.34", "24.615")
    }

    @Test
    fun `a cross-chain token transfer keeps the minimum and the destination fee only`() = runTest {
        stubCrossChain()

        val amounts = useCase.calculateXcmTransfersQuickInputs(POLKADOT, DESTINATION, usdt.id)

        assertAmounts(amounts, "74.075", "49.35", "24.625")
    }

    @Test
    fun `a token transfer reserves no utility tip or fee`() = runTest {
        val amounts = useCase.calculateTransfersQuickInputs(POLKADOT, usdt.id)

        assertAmounts(amounts, "75", "50", "25")
        coVerify(exactly = 0) { repository.getTransferFee(any(), any(), any(), any()) }
    }

    @Test
    fun `a TON transfer quotes its fee with the forward amount and a comment`() = runTest {
        initializeTonAddressCode()
        val transfers = mutableListOf<Transfer>()
        coEvery { repository.getTransferFee(ton, capture(transfers), any(), any()) } returns BigDecimal("0.01")

        val amounts = useCase.calculateTransfersQuickInputs(TON, tonCoin.id)

        assertAmounts(amounts, "7.49", "4.99", "2.49")
        val first = transfers.first()
        val expectedAmount = BigDecimal("7.5").setScale(tonCoin.precision, RoundingMode.UP) + TON_BASE_FORWARD_AMOUNT
        assertEquals(0, first.amount.compareTo(expectedAmount))
        val params = first.additionalParams as TonTransferParams
        assertEquals(first.sender, params.comment)
        assertEquals(first.sender, first.recipient)
        assertTrue(first.sender.isNotEmpty())
    }

    private fun stubCrossChain() {
        coEvery { existentialDeposit.invoke(dot) } returns Result.success(BigInteger.TEN.pow(10))
        coEvery { existentialDeposit.invoke(usdt) } returns Result.success(BigInteger.TEN.pow(6))
        coEvery { xcm.getDestinationFee(POLKADOT, DESTINATION, any()) } returns BigDecimal("0.1")
        coEvery { xcm.getOriginFee(POLKADOT, DESTINATION, any(), any()) } returns BigDecimal("0.01")
    }

    private fun assertAmounts(actual: Map<Double, BigDecimal>, vararg values: String) {
        assertEquals(listOf(0.75, 0.5, 0.25), actual.keys.toList())
        actual.values.zip(values).forEach { (amount, expected) ->
            assertEquals("$amount != $expected", 0, amount.compareTo(BigDecimal(expected)))
        }
    }

    private companion object {
        const val SORA = "sora"
        const val POLKADOT = "polkadot"
        const val TON = "ton"
        const val DESTINATION = "asset-hub"
        const val XOR_ID = "0x0200000000000000000000000000000000000000000000000000000000000000"
        const val VAL_ID = "0x0200040000000000000000000000000000000000000000000000000000000000"
    }
}
