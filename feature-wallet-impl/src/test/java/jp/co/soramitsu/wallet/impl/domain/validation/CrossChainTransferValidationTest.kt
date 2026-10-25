package jp.co.soramitsu.wallet.impl.domain.validation

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
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
 * Cross-chain validation must never take an unknown destination asset, recipient balance or minimum as zero, and the
 * confirm-time check counts only what arrives after the destination fee.
 */
class CrossChainTransferValidationTest {

    private val polkadot = registry.single { it.id == polkadotChainId }
    private val assetHub = registry.single { it.id == POLKADOT_ASSET_HUB_ID }
    private val moonbeam = registry.single { it.id == MOONBEAM_ID }
    private val dot = polkadot.assets.single { it.isUtility }
    private val assetHubDot = assetHub.assets.single { it.symbol == "dot" }
    private val moonbeamDot = moonbeam.assets.single { it.symbol == "xcdot" }

    private val sender = ByteArray(32) { 1 }
    private val senderAddress = sender.toAddress(polkadot.addressPrefix.toShort())
    private val recipientAddress = ByteArray(32) { 2 }.toAddress(assetHub.addressPrefix.toShort())
    private val evmRecipient = "0x" + "ab".repeat(20)

    // Asset ids repeat across networks (DOT has the same id on Polkadot and Asset Hub), so minimums are keyed by both.
    private val minimums = mutableMapOf(
        dot.key to Result.success(ONE_DOT),
        assetHubDot.key to Result.success(CENTI_DOT),
        moonbeamDot.key to Result.success(DECI_DOT)
    )
    private var destinationBalance: () -> BigInteger = { BigInteger.ZERO }

    @Test
    fun `setup stops when the destination network has no copy of the asset`() = runBlocking {
        val withoutDot = assetHub.copy(assets = assetHub.assets.filterNot { it.symbol == "dot" })

        val result = validation(withoutDot).setup(amount = CENTI_DOT * TWO)

        assertEquals(TransferValidationResult.CrossChainDestinationUnavailable, result)
    }

    @Test
    fun `setup stops when two destination assets carry the symbol`() = runBlocking {
        val ambiguous = assetHub.copy(assets = assetHub.assets + assetHubDot.copy(id = "second-dot"))

        val result = validation(ambiguous).setup(amount = CENTI_DOT * TWO)

        assertEquals(TransferValidationResult.CrossChainDestinationUnavailable, result)
    }

    @Test
    fun `setup stops when the recipient balance cannot be read`() = runBlocking {
        destinationBalance = { throw IOException("socket closed") }

        val result = validation().setup(amount = CENTI_DOT * TWO)

        assertEquals(TransferValidationResult.CrossChainDestinationUnavailable, result)
    }

    @Test
    fun `setup stops when the destination minimum is unknown`() = runBlocking {
        minimums[assetHubDot.key] = unknown(assetHubDot)

        val result = validation().setup(amount = CENTI_DOT * TWO)

        assertEquals(TransferValidationResult.ExistentialDepositUnavailable("DOT"), result)
    }

    @Test
    fun `setup stops when the origin minimum is unknown`() = runBlocking {
        minimums[dot.key] = unknown(dot)

        val result = validation().setup(amount = CENTI_DOT * TWO)

        assertEquals(TransferValidationResult.ExistentialDepositUnavailable("DOT"), result)
    }

    @Test
    fun `setup refuses a recipient with surrounding whitespace`() = runBlocking {
        val result = validation().validateXcmTransfer(
            amountInPlanks = CENTI_DOT * TWO,
            originAsset = dotBalance(),
            destinationChainId = assetHub.id,
            destinationAddress = "$recipientAddress\n",
            originAddress = senderAddress,
            originFee = FEE,
            confirmedValidations = emptyList(),
            transferMyselfAvailable = true,
            destinationFee = BigDecimal.ZERO
        ).getOrThrow()

        assertEquals(TransferValidationResult.InvalidAddress, result)
    }

    @Test
    fun `setup waits for the origin fee before checking anything`() = runBlocking {
        val result = validation().validateXcmTransfer(
            amountInPlanks = CENTI_DOT * TWO,
            originAsset = dotBalance(),
            destinationChainId = assetHub.id,
            destinationAddress = recipientAddress,
            originAddress = senderAddress,
            originFee = null,
            confirmedValidations = emptyList(),
            transferMyselfAvailable = true,
            destinationFee = BigDecimal.ZERO
        ).getOrThrow()

        assertEquals(TransferValidationResult.WaitForFee, result)
    }

    @Test
    fun `setup refuses a transfer to the sender's own address unless that is allowed`() = runBlocking {
        fun setupToSelf(allowed: Boolean) = runBlocking {
            validation().validateXcmTransfer(
                amountInPlanks = CENTI_DOT * TWO,
                originAsset = dotBalance(),
                destinationChainId = assetHub.id,
                destinationAddress = senderAddress,
                originAddress = senderAddress,
                originFee = FEE,
                confirmedValidations = emptyList(),
                transferMyselfAvailable = allowed,
                destinationFee = BigDecimal.ZERO
            ).getOrThrow()
        }

        assertEquals(TransferValidationResult.TransferToTheSameAddress, setupToSelf(allowed = false))
        assertEquals(TransferValidationResult.Valid, setupToSelf(allowed = true))
    }

    @Test
    fun `setup refuses an amount that the balance cannot cover together with the fee`() = runBlocking {
        // The whole balance of 10 DOT leaves nothing for the origin fee.
        val result = validation().setup(amount = ONE_DOT * BigInteger.TEN)

        assertEquals(TransferValidationResult.InsufficientBalance, result)
    }

    @Test
    fun `setup warns when the sender would fall below the origin minimum until the warning is confirmed`() = runBlocking {
        // 10 DOT - 9.5 DOT - 0.016 DOT fee leaves 0.484 DOT, below the 1 DOT minimum of Polkadot.
        val amount = ONE_DOT * BigInteger("95") / BigInteger.TEN
        val warning = validation().setup(amount = amount)

        assertTrue(warning is TransferValidationResult.ExistentialDepositError)
        assertEquals(TransferValidationResult.Valid, validation().setup(amount = amount, confirmed = listOf(warning)))
    }

    @Test
    fun `setup refuses a recipient in another network's address format`() = runBlocking {
        fun setupTo(recipient: String) = runBlocking {
            validation().validateXcmTransfer(
                amountInPlanks = CENTI_DOT * TWO,
                originAsset = dotBalance(),
                destinationChainId = assetHub.id,
                destinationAddress = recipient,
                originAddress = senderAddress,
                originFee = FEE,
                confirmedValidations = emptyList(),
                transferMyselfAvailable = true,
                destinationFee = BigDecimal.ZERO
            ).getOrThrow()
        }
        val recipient = ByteArray(32) { 2 }

        // The same account in the generic and Kusama formats; Asset Hub uses the Polkadot format.
        assertEquals(TransferValidationResult.InvalidAddress, setupTo(recipient.toAddress(42.toShort())))
        assertEquals(TransferValidationResult.InvalidAddress, setupTo(recipient.toAddress(2.toShort())))
        assertEquals(TransferValidationResult.Valid, setupTo(recipient.toAddress(assetHub.addressPrefix.toShort())))
    }

    @Test
    fun `setup refuses a mixed-case EVM recipient whose checksum is wrong`() = runBlocking {
        fun setupTo(recipient: String) = runBlocking {
            validation(moonbeam).validateXcmTransfer(
                amountInPlanks = DECI_DOT * TWO,
                originAsset = dotBalance(),
                destinationChainId = moonbeam.id,
                destinationAddress = recipient,
                originAddress = senderAddress,
                originFee = FEE,
                confirmedValidations = emptyList(),
                transferMyselfAvailable = true,
                destinationFee = BigDecimal.ZERO
            ).getOrThrow()
        }

        assertEquals(TransferValidationResult.InvalidAddress, setupTo("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAeD"))
        assertEquals(TransferValidationResult.Valid, setupTo("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"))
    }

    @Test
    fun `setup rejects an amount that leaves an empty recipient below the destination minimum`() = runBlocking {
        val result = validation().setup(amount = CENTI_DOT / BigInteger.TEN)

        assertTrue(result is TransferValidationResult.DeadRecipient)
    }

    @Test
    fun `setup accepts an amount that reaches the destination minimum`() = runBlocking {
        val result = validation().setup(amount = CENTI_DOT * TWO)

        assertEquals(TransferValidationResult.Valid, result)
    }

    @Test
    fun `setup counts the existing recipient balance`() = runBlocking {
        destinationBalance = { CENTI_DOT }

        val result = validation().setup(amount = CENTI_DOT / BigInteger.TEN)

        assertEquals(TransferValidationResult.Valid, result)
    }

    @Test
    fun `confirmation subtracts the destination fee from what arrives`() = runBlocking {
        val fee = CENTI_DOT * BigInteger.valueOf(4) / BigInteger.TEN
        val userAmount = CENTI_DOT * BigInteger.valueOf(8) / BigInteger.TEN

        // 0.012 DOT leaves the account, but only 0.008 DOT arrives: below the 0.01 DOT minimum.
        val belowMinimum = validation().confirm(sent = userAmount + fee, destinationFee = fee)
        val aboveMinimum = validation().confirm(sent = CENTI_DOT + fee + BigInteger.ONE, destinationFee = fee)

        assertTrue(belowMinimum is TransferValidationResult.DeadRecipient)
        assertEquals(TransferValidationResult.Valid, aboveMinimum)
    }

    @Test
    fun `confirmation finds an xc prefixed destination asset as setup does`() = runBlocking {
        // xcDOT on Moonbeam has a 0.1 DOT minimum. An exact symbol match missed it and checked nothing.
        val result = validation(moonbeam).confirm(
            sent = CENTI_DOT * BigInteger.valueOf(5),
            destinationFee = BigInteger.ZERO,
            destinationChain = moonbeam,
            recipient = evmRecipient
        )

        assertTrue(result is TransferValidationResult.DeadRecipient)
    }

    @Test
    fun `confirmation stops when the recipient balance cannot be read`() = runBlocking {
        destinationBalance = { throw IOException("socket closed") }

        val result = validation().confirm(sent = CENTI_DOT * TWO, destinationFee = BigInteger.ZERO)

        assertEquals(TransferValidationResult.CrossChainDestinationUnavailable, result)
    }

    @Test
    fun `confirmation stops when the destination minimum is unknown`() = runBlocking {
        minimums[assetHubDot.key] = unknown(assetHubDot)

        val result = validation().confirm(sent = CENTI_DOT * TWO, destinationFee = BigInteger.ZERO)

        assertEquals(TransferValidationResult.ExistentialDepositUnavailable("DOT"), result)
    }

    @Test
    fun `confirmation rejects a destination fee larger than the amount`() = runBlocking {
        val result = validation().validateExistentialDeposit(
            amountInPlanks = CENTI_DOT,
            originAsset = dotBalance(),
            destinationChainId = assetHub.id,
            destinationAddress = recipientAddress,
            originAddress = senderAddress,
            originFee = FEE,
            destinationFeeInPlanks = CENTI_DOT + BigInteger.ONE
        )

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `a transfer on one network checks the asset that is sent`() = runBlocking {
        // Another asset with the same symbol and a much larger minimum must not be taken for the sent one.
        val impostor = dot.copy(id = "impostor-dot", isUtility = false)
        val polkadotWithImpostor = polkadot.copy(assets = listOf(impostor) + polkadot.assets)
        minimums[impostor.key] = Result.success(ONE_DOT * BigInteger.valueOf(1_000))
        val recipient = ByteArray(32) { 3 }.toAddress(polkadot.addressPrefix.toShort())

        val result = validation(polkadotWithImpostor, origin = polkadotWithImpostor).validateExistentialDeposit(
            amountInPlanks = ONE_DOT * TWO,
            originAsset = dotBalance(),
            destinationChainId = polkadot.id,
            destinationAddress = recipient,
            originAddress = senderAddress,
            originFee = FEE
        )

        assertEquals(TransferValidationResult.Valid, result.getOrThrow())
    }

    private suspend fun ValidateTransferUseCaseImpl.setup(
        amount: BigInteger,
        confirmed: List<TransferValidationResult> = emptyList()
    ): TransferValidationResult =
        validateXcmTransfer(
            amountInPlanks = amount,
            originAsset = dotBalance(),
            destinationChainId = assetHub.id,
            destinationAddress = recipientAddress,
            originAddress = senderAddress,
            originFee = FEE,
            confirmedValidations = confirmed,
            transferMyselfAvailable = true,
            destinationFee = BigDecimal.ZERO
        ).getOrThrow()

    private suspend fun ValidateTransferUseCaseImpl.confirm(
        sent: BigInteger,
        destinationFee: BigInteger,
        destinationChain: Chain = assetHub,
        recipient: String = recipientAddress
    ): TransferValidationResult = validateExistentialDeposit(
        amountInPlanks = sent,
        originAsset = dotBalance(),
        destinationChainId = destinationChain.id,
        destinationAddress = recipient,
        originAddress = senderAddress,
        originFee = FEE,
        destinationFeeInPlanks = destinationFee
    ).getOrThrow()

    private val CoreAsset.key: String get() = "$chainId:$id"

    private fun unknown(asset: CoreAsset): Result<BigInteger> =
        Result.failure(ExistentialDepositUnavailableException(asset, IllegalStateException("Runtime is not loaded")))

    private fun validation(destination: Chain = assetHub, origin: Chain = polkadot): ValidateTransferUseCaseImpl {
        val existentialDepositUseCase = object : ExistentialDepositUseCase {
            override suspend fun invoke(chainAsset: CoreAsset): Result<BigInteger> =
                minimums[chainAsset.key] ?: error("No minimum fixture for ${chainAsset.symbol} on ${chainAsset.chainId}")
        }
        val chainsRepository = mockk<ChainsRepository> {
            coEvery { getChain(origin.id) } returns origin
            coEvery { getChain(destination.id) } returns destination
        }
        val accountRepository = mockk<AccountRepository> {
            coEvery { getSelectedMetaAccount() } returns metaAccount()
        }
        val walletRepository = mockk<WalletRepository> {
            coEvery { getTotalBalance(any(), any(), any()) } answers { destinationBalance() }
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

    private fun dotBalance() = Asset(
        metaId = 1,
        token = Token(configuration = dot, fiatRate = null, fiatSymbol = null, recentRateChange = null),
        accountId = sender,
        freeInPlanks = ONE_DOT * BigInteger.TEN,
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
        ethereumAddress = null,
        ethereumPublicKey = null,
        tonPublicKey = null,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "Wallet",
        initialized = true
    )

    private companion object {
        const val POLKADOT_ASSET_HUB_ID = "68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f"
        const val MOONBEAM_ID = "fe58ea77779b7abda7da4ec526d14db9b1e9cd40a217c34892af80a9b332b76d"
        val TWO: BigInteger = BigInteger.TWO
        val ONE_DOT: BigInteger = BigInteger.TEN.pow(10)
        val DECI_DOT: BigInteger = BigInteger.TEN.pow(9)
        val CENTI_DOT: BigInteger = BigInteger.TEN.pow(8)
        val FEE: BigInteger = BigInteger("160000000")

        val registry: List<Chain> by lazy {
            val file = listOf(File("../runtime/src/main/assets/local_chains.json"), File("runtime/src/main/assets/local_chains.json"))
                .first(File::exists)
            val type = object : TypeToken<List<ChainRemote>>() {}.type
            Gson().fromJson<List<ChainRemote>>(file.readText(), type).map(ChainRemote::toChain)
        }
    }
}
