package jp.co.soramitsu.wallet.impl.domain.validation

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
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
 * AL-10: when the network minimum cannot be read, send validation says so instead of treating it as zero
 * (4.2.0 and the RC passed every minimum check in that case). The user can still continue after reading it.
 */
class UnknownExistentialDepositValidationTest {

    private val polkadot = registry.single { it.id == polkadotChainId }
    private val kusama = registry.single { it.id == kusamaChainId }
    private val assetHub = registry.single { it.id == "68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f" }
    private val dot = polkadot.assets.single { it.isUtility }

    private val sender = ByteArray(32) { 1 }
    private val senderAddress = sender.toAddress(polkadot.addressPrefix.toShort())
    private val recipientAddress = ByteArray(32) { 2 }.toAddress(polkadot.addressPrefix.toShort())

    @Test
    fun `send reports an unknown minimum before the minimum checks`() = runBlocking {
        val validate = validation(existentialDeposit = { unknown(it) })

        val result = validate.validateTransfer(
            amountInPlanks = ONE_DOT,
            asset = dotBalance(free = TEN_DOT),
            destinationAddress = recipientAddress,
            senderAddress = senderAddress,
            fee = FEE,
            confirmedValidations = emptyList(),
            transferMyselfAvailable = false,
            skipEdValidation = false
        )

        assertEquals(TransferValidationResult.ExistentialDepositUnknown("DOT"), result.getOrThrow())
    }

    @Test
    fun `send continues once the user confirmed the unknown minimum`() = runBlocking {
        val validate = validation(existentialDeposit = { unknown(it) })

        val result = validate.validateTransfer(
            amountInPlanks = ONE_DOT,
            asset = dotBalance(free = TEN_DOT),
            destinationAddress = recipientAddress,
            senderAddress = senderAddress,
            fee = FEE,
            confirmedValidations = listOf(TransferValidationResult.ExistentialDepositUnknown("DOT")),
            transferMyselfAvailable = false,
            skipEdValidation = false
        )

        assertEquals(TransferValidationResult.Valid, result.getOrThrow())
    }

    @Test
    fun `insufficient balance still comes first`() = runBlocking {
        val validate = validation(existentialDeposit = { unknown(it) })

        val result = validate.validateTransfer(
            amountInPlanks = TEN_DOT,
            asset = dotBalance(free = ONE_DOT),
            destinationAddress = recipientAddress,
            senderAddress = senderAddress,
            fee = FEE,
            confirmedValidations = emptyList(),
            transferMyselfAvailable = false,
            skipEdValidation = false
        )

        assertEquals(TransferValidationResult.InsufficientBalance, result.getOrThrow())
    }

    @Test
    fun `known minimum still warns before the account is removed`() = runBlocking {
        val validate = validation(existentialDeposit = { Result.success(ONE_DOT) })

        val result = validate.validateTransfer(
            amountInPlanks = ONE_DOT,
            asset = dotBalance(free = ONE_DOT + FEE + BigInteger.ONE),
            destinationAddress = recipientAddress,
            senderAddress = senderAddress,
            fee = FEE,
            confirmedValidations = emptyList(),
            transferMyselfAvailable = false,
            skipEdValidation = false
        )

        assertTrue(result.getOrThrow() is TransferValidationResult.ExistentialDepositWarning)
    }

    @Test
    fun `confirm screen check reports the unknown minimum on the same network`() = runBlocking {
        val validate = validation(existentialDeposit = { unknown(it) })

        val result = validate.validateExistentialDeposit(
            amountInPlanks = ONE_DOT,
            originAsset = dotBalance(free = TEN_DOT),
            destinationChainId = polkadot.id,
            destinationAddress = recipientAddress,
            originAddress = senderAddress,
            originFee = FEE
        )

        assertEquals(TransferValidationResult.ExistentialDepositUnknown("DOT"), result.getOrThrow())
    }

    @Test
    fun `cross-chain check stops when a minimum is unknown`() = runBlocking {
        val validate = validation(existentialDeposit = { unknown(it) })

        val result = validate.validateExistentialDeposit(
            amountInPlanks = ONE_DOT,
            originAsset = dotBalance(free = TEN_DOT),
            destinationChainId = assetHub.id,
            destinationAddress = recipientAddress,
            originAddress = senderAddress,
            originFee = FEE
        )

        // An error, not the warning a transfer on one network shows: the user cannot continue past it.
        assertEquals(TransferValidationResult.ExistentialDepositUnavailable("DOT"), result.getOrThrow())
    }

    @Test
    fun `unknown minimums of the asset and the fee asset are named once`() {
        val check = existentialDepositUnknownCheck(
            dot to unknown(dot),
            polkadot.utilityAsset to unknown(dot),
            kusama.utilityAsset to Result.success(BigInteger.ONE),
            null to unknown(dot)
        )

        assertEquals(TransferValidationResult.ExistentialDepositUnknown("DOT") to true, check)
    }

    private fun unknown(asset: CoreAsset): Result<BigInteger> =
        Result.failure(ExistentialDepositUnavailableException(asset, IllegalStateException("Method not found")))

    private fun validation(existentialDeposit: (CoreAsset) -> Result<BigInteger>): ValidateTransferUseCaseImpl {
        val existentialDepositUseCase = object : ExistentialDepositUseCase {
            override suspend fun invoke(chainAsset: CoreAsset) = existentialDeposit(chainAsset)
        }
        val chainsRepository = mockk<ChainsRepository> {
            coEvery { getChain(polkadot.id) } returns polkadot
            coEvery { getChain(kusama.id) } returns kusama
            coEvery { getChain(assetHub.id) } returns assetHub
        }
        val accountRepository = mockk<AccountRepository> {
            coEvery { getSelectedMetaAccount() } returns metaAccount()
        }
        val walletRepository = mockk<WalletRepository> {
            coEvery { getTotalBalance(any(), any(), any()) } returns BigInteger.ZERO
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

    private fun dotBalance(free: BigInteger) = Asset(
        metaId = 1,
        token = Token(configuration = dot, fiatRate = null, fiatSymbol = null, recentRateChange = null),
        accountId = sender,
        freeInPlanks = free,
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
        val ONE_DOT: BigInteger = BigInteger.TEN.pow(10)
        val TEN_DOT: BigInteger = BigInteger.TEN.pow(11)
        val FEE: BigInteger = BigInteger("160000000")

        val registry: List<Chain> by lazy {
            val file = listOf(File("../runtime/src/main/assets/local_chains.json"), File("runtime/src/main/assets/local_chains.json"))
                .first(File::exists)
            val type = object : TypeToken<List<ChainRemote>>() {}.type
            Gson().fromJson<List<ChainRemote>>(file.readText(), type).map(ChainRemote::toChain)
        }
    }
}
