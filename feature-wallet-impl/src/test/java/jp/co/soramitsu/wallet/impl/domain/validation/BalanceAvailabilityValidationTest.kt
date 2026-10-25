package jp.co.soramitsu.wallet.impl.domain.validation

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.*
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import jp.co.soramitsu.wallet.api.domain.*
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.math.BigDecimal
import jp.co.soramitsu.core.models.Asset as CoreAsset

class BalanceAvailabilityValidationTest {
    private class Fixture {
        val chain = registry.single { it.id == polkadotChainId }
        val dot = chain.assets.single { it.isUtility }
        val accountId = ByteArray(32) { 1 }
        val account = MetaAccount(1, emptyMap(), emptyMap(), accountId, CryptoType.SR25519, accountId,
            null, null, null, true, true, null, "Fixture", true)
        val chains = mockk<ChainsRepository>()
        val accounts = mockk<AccountRepository>()
        val repository = mockk<WalletRepository>()
        val constants = mockk<WalletConstants>()
        val ed = mockk<ExistentialDepositUseCase>()
        val swap = mockk<PolkaswapInteractor>()
        val validator = ValidateTransferUseCaseImpl(ed, constants, chains, accounts, repository, swap)
        init {
            coEvery { chains.getChain(chain.id) } returns chain
            coEvery { accounts.getSelectedMetaAccount() } returns account
            coEvery { repository.getTotalBalance(any(), any(), any()) } returns BigInteger.ZERO
            coEvery { repository.getAsset(any(), any(), any(), any()) } returns null
            coEvery { constants.tip(any()) } returns BigInteger.ZERO
            coEvery { ed.invoke(any()) } returns Result.success(BigInteger.ZERO)
        }
        fun balance(value: BigInteger?, utility: Boolean = true, precision: Int = dot.precision, currencyId: String = "token"): Asset {
            val config: CoreAsset = if (utility) dot else mockk(relaxed = true) {
                every { chainId } returns chain.id
                every { id } returns "token"
                every { symbol } returns "TOKEN"
                every { this@mockk.precision } returns precision
                every { type } returns dot.type
                every { isUtility } returns false
                every { this@mockk.currencyId } returns currencyId
            }
            val token = Token(config, null, null, null)
            return mockk {
                every { this@mockk.token } returns token
                every { transferableInPlanks } returns value
                every { sendAvailableInPlanks } returns value
                every { freeInPlanks } returns value
            }
        }
        fun providers(): List<ValidationChecksProvider> = listOf(
            EthereumValidationChecksProvider(chains, accounts, ed, repository),
            TonValidationChecksProvider(chains, accounts, repository),
            SubstrateValidationChecksProvider(accounts, repository, chains, ed, constants, swap)
        )
    }

    @Test fun allProvidersStopUnknownOriginBeforeAnyNetworkRead() = runTest {
        val f = Fixture()
        for (provider in f.providers()) {
            assertEquals(mapOf(TransferValidationResult.BalanceUnavailable to true),
                provider.provide(BigInteger.ONE, f.balance(null), "recipient", BigInteger.ONE))
        }
        coVerify(exactly = 0) { f.chains.getChain(any()) }
        coVerify(exactly = 0) { f.ed.invoke(any()) }
    }

    @Test fun absentAndUnknownFeeHoldingsRemainUnavailableForEveryProvider() = runTest {
        val f = Fixture()
        for (feeAsset in listOf(null, f.balance(null))) {
            coEvery { f.repository.getAsset(any(), any(), any(), any()) } returns feeAsset
            for (provider in f.providers()) {
                assertEquals(mapOf(TransferValidationResult.BalanceUnavailable to true),
                    provider.provide(BigInteger.ONE, f.balance(BigInteger.TEN, utility = false), "recipient", BigInteger.ONE))
            }
        }
    }

    @Test fun knownZeroFeeHoldingIsInsufficientRatherThanUnavailable() = runTest {
        val f = Fixture()
        coEvery { f.repository.getAsset(any(), any(), any(), any()) } returns f.balance(BigInteger.ZERO)
        for (provider in f.providers()) {
            val checks = provider.provide(BigInteger.ONE, f.balance(BigInteger.TEN, utility = false), "recipient", BigInteger.ONE)
            assertEquals(true, checks[TransferValidationResult.InsufficientUtilityAssetBalance])
            assertFalse(checks[TransferValidationResult.BalanceUnavailable] == true)
        }
    }

    @Test fun unavailableSendBalanceCannotBeConfirmedOrSkipped() = runTest {
        val f = Fixture()
        for (skipMinimum in listOf(false, true)) {
            val result = f.validator.validateTransfer(BigInteger.ZERO, f.balance(null), "recipient", "sender", BigInteger.ZERO,
                listOf(TransferValidationResult.BalanceUnavailable), true, skipMinimum)
            assertSame(TransferValidationResult.BalanceUnavailable, result.getOrThrow())
        }
        coVerify(exactly = 0) { f.chains.getChain(any()) }
    }

    @Test fun unavailableCrossChainAndConfirmationBalancesStopBeforeNetworkWork() = runTest {
        val f = Fixture()
        assertSame(TransferValidationResult.BalanceUnavailable, f.validator.validateXcmTransfer(
            BigInteger.ONE, f.balance(null), "destination", "recipient", "sender", BigInteger.ONE,
            listOf(TransferValidationResult.BalanceUnavailable), false, null).getOrThrow())
        assertSame(TransferValidationResult.BalanceUnavailable, f.validator.validateExistentialDeposit(
            BigInteger.ONE, f.balance(null), "destination", "recipient", "sender", BigInteger.ONE,
            listOf(TransferValidationResult.BalanceUnavailable)).getOrThrow())
        coVerify(exactly = 0) { f.chains.getChain(any()) }
        coVerify(exactly = 0) { f.ed.invoke(any()) }
    }

    @Test fun cancelledValidationIsNotWrappedAsAnOrdinaryResult() = runTest {
        val f = Fixture(); val cancelled = CancellationException("stop validation")
        coEvery { f.chains.getChain(any()) } throws cancelled
        assertSame(cancelled, runCatching {
            f.validator.validateTransfer(BigInteger.ONE, f.balance(BigInteger.TEN), "recipient", "sender", BigInteger.ONE,
                emptyList(), false, false)
        }.exceptionOrNull())
    }

    @Test fun feeConversionUsesUtilityPrecisionAndNeverTreatsMissingQuoteAsFree() = runTest {
        val f = Fixture()
        val input = f.balance(BigInteger("1000"), utility = false, precision = 2,
            currencyId = jp.co.soramitsu.runtime.multiNetwork.chain.model.bokoloCashTokenId)
        coEvery { f.repository.getAsset(any(), any(), any(), any()) } returns f.balance(BigInteger.ZERO)
        val quote = mockk<jp.co.soramitsu.polkaswap.api.domain.models.SwapDetails> {
            every { amount } returns BigDecimal("2")
        }
        coEvery { f.swap.calcDetails(any(), any(), any(), any(), any(), any(), any()) } returns Result.success(quote)
        val fee = BigInteger.TEN.pow(f.dot.precision)
        val checks = f.providers()[2].provide(BigInteger("800"), input, "recipient", fee)
        coVerify(exactly = 1) { f.swap.calcDetails(any(), input, any(), match { it.compareTo(BigDecimal.ONE) == 0 }, any(), any(), any()) }
        assertEquals(true, checks[TransferValidationResult.InsufficientBalance])
        coEvery { f.swap.calcDetails(any(), any(), any(), any(), any(), any(), any()) } returns Result.success(null)
        assertNotNull(runCatching { f.providers()[2].provide(BigInteger("800"), input, "recipient", fee) }.exceptionOrNull())
    }

    @Test fun cancelledMinimumCannotBeMaskedByAnAbsentUtilityBalance() = runTest {
        val f = Fixture(); val cancelled = CancellationException("minimum cancelled before utility read")
        coEvery { f.ed.invoke(any()) } returns Result.failure(cancelled)
        for (provider in listOf(f.providers()[0], f.providers()[2])) {
            assertSame(cancelled, runCatching {
                provider.provide(BigInteger.ONE, f.balance(BigInteger.TEN, utility = false), "recipient", BigInteger.ONE)
            }.exceptionOrNull())
        }
        coVerify(exactly = 0) { f.repository.getAsset(any(), any(), any(), any()) }
    }

    @Test fun recipientReadCancellationIsNotReplacedWithAZeroBalance() = runTest {
        val f = Fixture(); val cancelled = CancellationException("recipient read cancelled")
        coEvery { f.repository.getTotalBalance(any(), any(), any()) } throws cancelled
        assertSame(cancelled, runCatching {
            f.providers()[2].provide(BigInteger.ONE, f.balance(BigInteger.TEN), "recipient", BigInteger.ONE)
        }.exceptionOrNull())
        val recipient = ByteArray(32) { 2 }.toAddress(f.chain.addressPrefix.toShort())
        val sender = f.accountId.toAddress(f.chain.addressPrefix.toShort())
        assertSame(cancelled, runCatching {
            f.validator.validateXcmTransfer(BigInteger.ONE, f.balance(BigInteger.TEN), f.chain.id,
                recipient, sender, BigInteger.ONE, emptyList(), false, null)
        }.exceptionOrNull())
    }

    @Test fun cancelledMinimumCannotBecomeAConfirmableUnknownMinimumWarning() {
        val f = Fixture(); val cancelled = CancellationException("stop minimum read")
        assertSame(cancelled, runCatching {
            existentialDepositUnknownCheck(f.dot to Result.failure(cancelled))
        }.exceptionOrNull())
    }

    @Test fun availabilityMessageUsesLocalizedTextAndIsNotAWarning() {
        val resources = mockk<ResourceManager> { every { getString(any()) } answers { "text-${firstArg<Int>()}" } }
        val error = ValidationException.fromValidationResult(TransferValidationResult.BalanceUnavailable, resources)
        assertNotNull(error)
        assertFalse(error is jp.co.soramitsu.common.base.errors.ValidationWarning)
        assertEquals("text-${R.string.portfolio_balance_unavailable}", error!!.component2())
    }

    private companion object {
        val registry: List<Chain> by lazy {
            val file = listOf(File("../runtime/src/main/assets/local_chains.json"), File("runtime/src/main/assets/local_chains.json")).first(File::exists)
            val type = object : TypeToken<List<ChainRemote>>() {}.type
            Gson().fromJson<List<ChainRemote>>(file.readText(), type).map(ChainRemote::toChain)
        }
    }
}
