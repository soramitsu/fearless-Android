package jp.co.soramitsu.wallet.impl.domain

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.*
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger

class QuickInputsAvailabilityTest {
    private class Fixture {
        val chain = registry.single { it.id == polkadotChainId }
        val config = chain.assets.single { it.isUtility }
        val accounts = mockk<AccountRepository>()
        val chains = mockk<ChainsRepository>()
        val repository = mockk<WalletRepository>()
        val constants = mockk<WalletConstants>()
        val ed = mockk<ExistentialDepositUseCase>()
        val xcm = mockk<XcmInteractor>()
        val swap = mockk<PolkaswapInteractor>()
        val useCase = QuickInputsUseCaseImpl(repository, accounts, chains, constants, ed, xcm, swap, Dispatchers.Unconfined)
        val accountId = ByteArray(32) { 1 }
        val account = MetaAccount(1, emptyMap(), emptyMap(), accountId, CryptoType.SR25519, accountId,
            null, null, null, true, true, null, "Fixture", true)
        val unknown = Asset.createEmpty(config, 1, accountId, minSupportedVersion = null)
        init {
            coEvery { chains.getChain(chain.id) } returns chain
            coEvery { accounts.getSelectedMetaAccount() } returns account
            coEvery { repository.getAsset(any(), any(), any(), any()) } returns unknown
            every { swap.polkaswapChainId } returns chain.id
            coEvery { constants.tip(any()) } returns null
        }
        fun available(value: String) {
            val amount = BigDecimal(value).scaleByPowerOfTen(config.precision).toBigIntegerExact()
            val zero = AssetAmountState.Known(BigInteger.ZERO)
            coEvery { repository.getAsset(any(), any(), any(), any()) } returns unknown.copy(
                freeInPlanksState = AssetAmountState.Known(amount), reservedInPlanksState = zero,
                miscFrozenInPlanksState = zero, feeFrozenInPlanksState = zero,
                bondedInPlanksState = zero, redeemableInPlanksState = zero, unbondingInPlanksState = zero)
        }
    }

    @Test fun unavailableStakingAmountStopsBeforeChainOrFeeWork() = runTest {
        val f = Fixture(); var fees = 0
        val actual = f.useCase.calculateStakingQuickInputs(f.chain.id, f.config.id, { null }, { fees++; BigInteger.ZERO })
        assertTrue(actual.isEmpty()); assertEquals(0, fees)
        coVerify(exactly = 0) { f.chains.getChain(any()) }
    }

    @Test fun knownStakingAmountSubtractsRealFeeAndKeepsKnownZero() = runTest {
        val f = Fixture()
        val fee = BigInteger.TEN.pow(f.config.precision)
        val actual = f.useCase.calculateStakingQuickInputs(f.chain.id, f.config.id, { BigDecimal("100") }, { fee })
        assertAmounts(actual, "74", "49", "24")
        val zero = f.useCase.calculateStakingQuickInputs(f.chain.id, f.config.id, { BigDecimal.ZERO }, { BigInteger.ZERO })
        assertAmounts(zero, "0", "0", "0")
    }

    @Test fun failedStakingFeeRemovesEntireSetInsteadOfOfferingUnreservedAmounts() = runTest {
        val f = Fixture(); var calls = 0
        val actual = f.useCase.calculateStakingQuickInputs(f.chain.id, f.config.id, { BigDecimal.TEN }, {
            if (++calls == 2) error("fee unavailable") else BigInteger.ZERO
        })
        assertTrue(actual.isEmpty()); assertEquals(2, calls)
    }

    @Test fun stakingFeeCancellationIsPropagatedWithItsExactCause() = runTest {
        val f = Fixture(); val cancelled = CancellationException("cancel fee")
        val failure = runCatching {
            f.useCase.calculateStakingQuickInputs(f.chain.id, f.config.id, { BigDecimal.TEN }, { throw cancelled })
        }.exceptionOrNull()
        assertSame(cancelled, failure)
    }

    @Test fun unknownTransferBalanceDoesNotReadTipOrFee() = runTest {
        val f = Fixture()
        assertTrue(f.useCase.calculateTransfersQuickInputs(f.chain.id, f.config.id).isEmpty())
        coVerify(exactly = 0) { f.constants.tip(any()) }
        coVerify(exactly = 0) { f.repository.getTransferFee(any(), any(), any(), any()) }
    }

    @Test fun unknownSwapBalanceDoesNotEstimateFee() = runTest {
        val f = Fixture()
        assertTrue(f.useCase.calculatePolkaswapQuickInputs(f.config.id, f.config.id).isEmpty())
        coVerify(exactly = 0) { f.swap.estimateSwapFee(any(), any(), any(), any(), any(), any()) }
    }

    @Test fun unknownCrossChainBalanceDoesNotReadMinimumOrFees() = runTest {
        val f = Fixture()
        assertTrue(f.useCase.calculateXcmTransfersQuickInputs(f.chain.id, "destination", f.config.id).isEmpty())
        coVerify(exactly = 0) { f.ed.invoke(any()) }
        coVerify(exactly = 0) { f.xcm.getDestinationFee(any(), any(), any()) }
    }

    @Test fun transferFeeFailureIsUnavailableAndCancellationIsNotSwallowed() = runTest {
        val f = Fixture(); f.available("100")
        coEvery { f.repository.getTransferFee(any(), any(), any(), any()) } throws IllegalStateException("fee unavailable")
        assertTrue(f.useCase.calculateTransfersQuickInputs(f.chain.id, f.config.id).isEmpty())
        val cancelled = CancellationException("cancel transfer fee")
        coEvery { f.repository.getTransferFee(any(), any(), any(), any()) } throws cancelled
        assertSame(cancelled, runCatching { f.useCase.calculateTransfersQuickInputs(f.chain.id, f.config.id) }.exceptionOrNull())
    }

    @Test fun knownTransferBalanceStillSubtractsFeeFromEachFraction() = runTest {
        val f = Fixture(); f.available("100")
        coEvery { f.repository.getTransferFee(any(), any(), any(), any()) } returns BigDecimal.ONE
        assertAmounts(f.useCase.calculateTransfersQuickInputs(f.chain.id, f.config.id), "74", "49", "24")
    }

    @Test fun missingDestinationFeeCannotBecomeZero() = runTest {
        val f = Fixture(); f.available("100")
        coEvery { f.ed.invoke(any()) } returns Result.success(BigInteger.ZERO)
        coEvery { f.xcm.getDestinationFee(any(), any(), any()) } returns null
        assertTrue(f.useCase.calculateXcmTransfersQuickInputs(f.chain.id, "destination", f.config.id).isEmpty())
    }

    private fun assertAmounts(actual: Map<Double, BigDecimal>, vararg values: String) {
        assertEquals(listOf(0.75, 0.5, 0.25), actual.keys.toList())
        actual.values.zip(values).forEach { (amount, expected) -> assertEquals(0, amount.compareTo(BigDecimal(expected))) }
    }

    private companion object {
        val registry: List<Chain> by lazy {
            val file = listOf(File("../runtime/src/main/assets/local_chains.json"), File("runtime/src/main/assets/local_chains.json")).first(File::exists)
            val type = object : TypeToken<List<ChainRemote>>() {}.type
            Gson().fromJson<List<ChainRemote>>(file.readText(), type).map(ChainRemote::toChain)
        }
    }
}
