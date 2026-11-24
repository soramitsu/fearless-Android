package jp.co.soramitsu.wallet.impl.domain.validation

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUnavailableException
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import jp.co.soramitsu.core.models.Asset as CoreAsset

/**
 * Cross-chain validation on the SORA bridge directions (minimums, destination fee warning, fee asset checks), the
 * non-utility origin branch, the confirm-time refusals, and the Ethereum and TON send providers.
 */
class ValidateTransferUseCaseCoverageTest {

    private val polkadot = chain(polkadotChainId)
    private val kusama = chain(kusamaChainId)
    private val sora = chain(soraMainChainId)
    private val acala = chain(ACALA_ID)
    private val ethereum = chain(ETHEREUM_ID)
    private val ton = chain(TON_ID)

    private val dot = polkadot.assets.single { it.isUtility }
    private val ksm = kusama.assets.single { it.isUtility }
    private val xor = sora.assets.single { it.isUtility }
    private val soraDot = sora.assets.single { it.symbol == "dot" }
    private val aca = acala.assets.single { it.isUtility }
    private val acalaDot = acala.assets.single { it.symbol == "dot" }
    private val eth = ethereum.assets.single { it.isUtility }
    private val tonCoin = ton.assets.single { it.isUtility }

    private val sender = ByteArray(32) { 1 }
    private val recipient = ByteArray(32) { 2 }
    private val ethereumSender = ByteArray(20) { 3 }

    private val minimums = mutableMapOf<String, Result<BigInteger>>(dot.key to Result.success(units(dot, "1")))
    private var recipientBalance: BigInteger = BigInteger.ZERO
    private var feeAsset: (CoreAsset) -> Asset? = { balance(it, "10") }

    @Test
    fun `SORA to Polkadot below the bridge minimum asks for 1_1 DOT`() = runBlocking {
        val result = soraToPolkadot(amount = units(soraDot, "1"))

        assertEquals(TransferValidationResult.SubstrateBridgeMinimumAmountRequired("1.1 DOT"), result)
    }

    @Test
    fun `SORA to Polkadot warns when the amount does not cover the destination fee`() = runBlocking {
        val result = soraToPolkadot(amount = units(soraDot, "2"), destinationFee = BigDecimal("3"))

        assertEquals(TransferValidationResult.SubstrateBridgeAmountLessThenFeeWarning(sora.name), result)
    }

    @Test
    fun `SORA to Polkadot needs XOR for the fee`() = runBlocking {
        feeAsset = { balance(it, "0") }

        val result = soraToPolkadot(amount = units(soraDot, "2"), destinationFee = BigDecimal("0.1"))

        assertEquals(TransferValidationResult.InsufficientUtilityAssetBalance, result)
    }

    @Test
    fun `SORA to Polkadot passes with enough DOT and XOR`() = runBlocking {
        val result = soraToPolkadot(amount = units(soraDot, "2"), destinationFee = BigDecimal("0.1"))

        assertEquals(TransferValidationResult.Valid, result)
    }

    @Test
    fun `SORA to Polkadot without a known XOR balance is unavailable`() = runBlocking {
        feeAsset = { null }

        val result = soraToPolkadot(amount = units(soraDot, "2"))

        assertEquals(TransferValidationResult.BalanceUnavailable, result)
    }

    @Test
    fun `SORA to Polkadot with an unknown XOR minimum stops`() = runBlocking {
        minimums[xor.key] = unknown(xor)

        val result = soraToPolkadot(amount = units(soraDot, "2"))

        assertEquals(TransferValidationResult.ExistentialDepositUnavailable("XOR"), result)
    }

    @Test
    fun `Kusama to SORA below the bridge minimum asks for 0_05 KSM`() = runBlocking {
        val result = xcm(
            origin = balance(ksm, "10"),
            destination = sora,
            amount = units(ksm, "0.01"),
            originAddress = sender.toAddress(kusama.addressPrefix.toShort())
        )

        assertEquals(TransferValidationResult.SubstrateBridgeMinimumAmountRequired("0.05 KSM"), result)
    }

    @Test
    fun `Polkadot to SORA counts the origin fee against the balance`() = runBlocking {
        val result = polkadotToSora(amount = units(dot, "10"))

        assertEquals(TransferValidationResult.InsufficientBalance, result)
    }

    @Test
    fun `Polkadot to SORA warns when the amount does not cover the destination fee`() = runBlocking {
        val result = polkadotToSora(amount = units(dot, "2"), destinationFee = BigDecimal("3"))

        assertEquals(TransferValidationResult.SubstrateBridgeAmountLessThenFeeWarning(sora.name), result)
    }

    @Test
    fun `Polkadot to SORA passes with enough DOT`() = runBlocking {
        val result = polkadotToSora(amount = units(dot, "2"), destinationFee = BigDecimal("0.1"))

        assertEquals(TransferValidationResult.Valid, result)
    }

    @Test
    fun `Polkadot to SORA without a known fee asset balance is unavailable`() = runBlocking {
        feeAsset = { null }

        val result = polkadotToSora(amount = units(dot, "2"))

        assertEquals(TransferValidationResult.BalanceUnavailable, result)
    }

    @Test
    fun `Acala to SORA with an unknown fee asset minimum stops`() = runBlocking {
        minimums[aca.key] = unknown(aca)

        val result = xcm(
            origin = balance(acalaDot, "10"),
            destination = sora,
            amount = units(acalaDot, "2"),
            originAddress = sender.toAddress(acala.addressPrefix.toShort())
        )

        assertEquals(TransferValidationResult.ExistentialDepositUnavailable("ACA"), result)
    }

    @Test
    fun `a non-utility asset needs the fee asset and its minimum`() = runBlocking {
        feeAsset = { balance(it, "0") }

        val first = acalaToPolkadot(confirmed = emptyList())
        val second = acalaToPolkadot(confirmed = listOf(first))

        assertEquals(TransferValidationResult.InsufficientUtilityAssetBalance, first)
        assertTrue(second is TransferValidationResult.UtilityExistentialDepositError)
    }

    @Test
    fun `a non-utility asset passes with a funded fee asset`() = runBlocking {
        assertEquals(TransferValidationResult.Valid, acalaToPolkadot(confirmed = emptyList()))
    }

    @Test
    fun `a non-utility asset without a known fee asset balance is unavailable`() = runBlocking {
        feeAsset = { null }

        assertEquals(TransferValidationResult.BalanceUnavailable, acalaToPolkadot(confirmed = emptyList()))
    }

    @Test
    fun `a non-utility asset with an unknown fee asset minimum stops`() = runBlocking {
        minimums[aca.key] = unknown(aca)

        assertEquals(TransferValidationResult.ExistentialDepositUnavailable("ACA"), acalaToPolkadot(confirmed = emptyList()))
    }

    @Test
    fun `an unknown send-available balance stops the cross-chain check`() = runBlocking {
        val partial = mockk<Asset> {
            every { token } returns Token(configuration = dot, fiatRate = null, fiatSymbol = null, recentRateChange = null)
            every { transferableInPlanks } returns units(dot, "10")
            every { sendAvailableInPlanks } returns null
        }

        val result = validation().validateXcmTransfer(
            amountInPlanks = units(dot, "2"),
            originAsset = partial,
            destinationChainId = sora.id,
            destinationAddress = recipient.toAddress(sora.addressPrefix.toShort()),
            originAddress = sender.toAddress(polkadot.addressPrefix.toShort()),
            originFee = FEE,
            confirmedValidations = emptyList(),
            transferMyselfAvailable = false,
            destinationFee = null
        ).getOrThrow()

        assertEquals(TransferValidationResult.BalanceUnavailable, result)
    }

    @Test
    fun `a transfer on one network refuses a destination fee`() = runBlocking {
        val result = validation().validateExistentialDeposit(
            amountInPlanks = units(dot, "2"),
            originAsset = balance(dot, "10"),
            destinationChainId = polkadot.id,
            destinationAddress = recipient.toAddress(polkadot.addressPrefix.toShort()),
            originAddress = sender.toAddress(polkadot.addressPrefix.toShort()),
            originFee = FEE,
            destinationFeeInPlanks = BigInteger.ONE
        )

        val failure = result.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals("A transfer on one network has no destination fee", failure?.message)
    }

    @Test
    fun `confirmation stops when the destination network has no copy of the asset`() = runBlocking {
        val result = validation().validateExistentialDeposit(
            amountInPlanks = units(dot, "2"),
            originAsset = balance(dot, "10"),
            destinationChainId = kusama.id,
            destinationAddress = recipient.toAddress(kusama.addressPrefix.toShort()),
            originAddress = sender.toAddress(polkadot.addressPrefix.toShort()),
            originFee = FEE
        ).getOrThrow()

        assertEquals(TransferValidationResult.CrossChainDestinationUnavailable, result)
    }

    @Test
    fun `confirmation of a non-utility asset checks only the recipient`() = runBlocking {
        val belowMinimum = acalaConfirmation(units(acalaDot, "0.1"))
        recipientBalance = units(dot, "1")
        val funded = acalaConfirmation(units(acalaDot, "0.1"))

        assertTrue(belowMinimum is TransferValidationResult.DeadRecipient)
        assertEquals(TransferValidationResult.Valid, funded)
    }

    @Test
    fun `an Ethereum send is checked by the Ethereum provider`() = runBlocking {
        val valid = send(balance(eth, "10"), units(eth, "1"), ETH_RECIPIENT, ETH_FEE)
        val tooMuch = send(balance(eth, "10"), units(eth, "10"), ETH_RECIPIENT, ETH_FEE)

        assertEquals(TransferValidationResult.Valid, valid)
        assertEquals(TransferValidationResult.InsufficientBalance, tooMuch)
    }

    @Test
    fun `a TON send is checked by the TON provider`() = runBlocking {
        val tonFee = units(tonCoin, "0.01")
        val valid = send(balance(tonCoin, "10"), units(tonCoin, "1"), TON_RECIPIENT, tonFee)
        val tooMuch = send(balance(tonCoin, "10"), units(tonCoin, "10"), TON_RECIPIENT, tonFee)

        assertEquals(TransferValidationResult.Valid, valid)
        assertEquals(TransferValidationResult.InsufficientBalance, tooMuch)
    }

    private suspend fun soraToPolkadot(amount: BigInteger, destinationFee: BigDecimal? = null) = xcm(
        origin = balance(soraDot, "10"),
        destination = polkadot,
        amount = amount,
        originAddress = sender.toAddress(sora.addressPrefix.toShort()),
        originFee = units(xor, "0.0007"),
        destinationFee = destinationFee
    )

    private suspend fun polkadotToSora(amount: BigInteger, destinationFee: BigDecimal? = null) = xcm(
        origin = balance(dot, "10"),
        destination = sora,
        amount = amount,
        originAddress = sender.toAddress(polkadot.addressPrefix.toShort()),
        destinationFee = destinationFee
    )

    private suspend fun acalaToPolkadot(confirmed: List<TransferValidationResult>) = xcm(
        origin = balance(acalaDot, "10"),
        destination = polkadot,
        amount = units(acalaDot, "2"),
        originAddress = sender.toAddress(acala.addressPrefix.toShort()),
        confirmed = confirmed
    )

    private suspend fun acalaConfirmation(amount: BigInteger): TransferValidationResult =
        validation().validateExistentialDeposit(
            amountInPlanks = amount,
            originAsset = balance(acalaDot, "10"),
            destinationChainId = polkadot.id,
            destinationAddress = recipient.toAddress(polkadot.addressPrefix.toShort()),
            originAddress = sender.toAddress(acala.addressPrefix.toShort()),
            originFee = FEE
        ).getOrThrow()

    @Suppress("LongParameterList")
    private suspend fun xcm(
        origin: Asset,
        destination: Chain,
        amount: BigInteger,
        originAddress: String,
        originFee: BigInteger = FEE,
        destinationFee: BigDecimal? = null,
        confirmed: List<TransferValidationResult> = emptyList()
    ): TransferValidationResult = validation().validateXcmTransfer(
        amountInPlanks = amount,
        originAsset = origin,
        destinationChainId = destination.id,
        destinationAddress = recipient.toAddress(destination.addressPrefix.toShort()),
        originAddress = originAddress,
        originFee = originFee,
        confirmedValidations = confirmed,
        transferMyselfAvailable = false,
        destinationFee = destinationFee
    ).getOrThrow()

    private suspend fun send(asset: Asset, amount: BigInteger, recipientAddress: String, fee: BigInteger) =
        validation().validateTransfer(
            amountInPlanks = amount,
            asset = asset,
            destinationAddress = recipientAddress,
            senderAddress = "sender",
            fee = fee,
            confirmedValidations = emptyList(),
            transferMyselfAvailable = false,
            skipEdValidation = false
        ).getOrThrow()

    private fun validation(): ValidateTransferUseCaseImpl {
        val chains = listOf(polkadot, kusama, sora, acala, ethereum, ton).associateBy(Chain::id)
        val existentialDepositUseCase = object : ExistentialDepositUseCase {
            override suspend fun invoke(chainAsset: CoreAsset): Result<BigInteger> =
                minimums[chainAsset.key] ?: Result.success(BigInteger.ZERO)
        }
        val chainsRepository = mockk<ChainsRepository> {
            coEvery { getChain(any()) } answers { chains.getValue(firstArg()) }
        }
        val accountRepository = mockk<AccountRepository> {
            coEvery { getSelectedMetaAccount() } returns metaAccount()
        }
        val walletRepository = mockk<WalletRepository> {
            coEvery { getTotalBalance(any(), any(), any()) } answers { recipientBalance }
            coEvery { getAsset(any(), any(), any(), any()) } answers { feeAsset(thirdArg()) }
        }
        val walletConstants = mockk<WalletConstants> {
            coEvery { tip(any()) } returns null
        }
        return ValidateTransferUseCaseImpl(
            existentialDepositUseCase = existentialDepositUseCase,
            walletConstants = walletConstants,
            chainsRepository = chainsRepository,
            accountRepository = accountRepository,
            walletRepository = walletRepository,
            polkaswapInteractor = mockk()
        )
    }

    private val CoreAsset.key: String get() = "$chainId:$id"

    private fun unknown(asset: CoreAsset): Result<BigInteger> =
        Result.failure(ExistentialDepositUnavailableException(asset, IllegalStateException("Runtime is not loaded")))

    private fun units(asset: CoreAsset, amount: String): BigInteger =
        BigDecimal(amount).movePointRight(asset.precision).toBigIntegerExact()

    private fun balance(asset: CoreAsset, free: String) = Asset(
        metaId = 1,
        token = Token(configuration = asset, fiatRate = null, fiatSymbol = null, recentRateChange = null),
        accountId = sender,
        freeInPlanks = units(asset, free),
        reservedInPlanks = BigInteger.ZERO,
        miscFrozenInPlanks = BigInteger.ZERO,
        feeFrozenInPlanks = BigInteger.ZERO,
        bondedInPlanks = null,
        redeemableInPlanks = null,
        unbondingInPlanks = null,
        sortIndex = 0,
        enabled = true,
        minSupportedVersion = null,
        chainAccountName = null,
        markedNotNeed = false,
        status = null
    )

    private fun metaAccount() = MetaAccount(
        id = 1,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = sender,
        substrateCryptoType = CryptoType.SR25519,
        substrateAccountId = sender,
        ethereumAddress = ethereumSender,
        ethereumPublicKey = null,
        tonPublicKey = ByteArray(32) { 4 },
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "Wallet",
        initialized = true
    )

    private companion object {
        const val ACALA_ID = "fc41b9bd8ef8fe53d58c7ea67c794c7ec9a73daf05e6d54b14ff6342c99ba64c"
        const val ETHEREUM_ID = "1"
        const val TON_ID = "-239"
        val ETH_RECIPIENT = "0x" + "ab".repeat(20)
        val TON_RECIPIENT = "0:" + "ab".repeat(32)
        val FEE: BigInteger = BigInteger("160000000")
        val ETH_FEE: BigInteger = BigInteger.TEN.pow(15)

        val registry: List<Chain> by lazy {
            val file = listOf(File("../runtime/src/main/assets/local_chains.json"), File("runtime/src/main/assets/local_chains.json"))
                .first(File::exists)
            val type = object : TypeToken<List<ChainRemote>>() {}.type
            Gson().fromJson<List<ChainRemote>>(file.readText(), type).map(ChainRemote::toChain)
        }

        fun chain(id: String): Chain = registry.single { it.id == id }
    }
}
