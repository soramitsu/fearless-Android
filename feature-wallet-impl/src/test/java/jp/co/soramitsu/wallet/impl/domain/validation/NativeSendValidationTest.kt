package jp.co.soramitsu.wallet.impl.domain.validation

import io.mockk.coEvery
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUnavailableException
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.BitcoinTransferServiceProviderTest
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

/** The send screen must accept Solana and Bitcoin recipients and leave balance, fee and rent rules to the network quote. */
class NativeSendValidationTest {
    private val solana = BitcoinTransferServiceProviderTest.solanaChain()
    private val bitcoin = BitcoinTransferServiceProviderTest.bitcoinChain()

    @Test
    fun `a Solana recipient is valid without Substrate minimum checks`() = runBlocking {
        val validate = validation()
        assertEquals(TransferValidationResult.Valid, validate.check(solana, SOLANA_RECIPIENT, SOLANA_SENDER))
        assertEquals(TransferValidationResult.InvalidAddress, validate.check(solana, SUBSTRATE_ADDRESS, SOLANA_SENDER))
        assertEquals(TransferValidationResult.InvalidAddress, validate.check(solana, BitcoinTransferServiceProviderTest.MAINNET_RECIPIENT, SOLANA_SENDER))
        assertEquals(TransferValidationResult.TransferToTheSameAddress, validate.check(solana, SOLANA_SENDER, SOLANA_SENDER))
        // The amount screen's minimum check has nothing to warn about on an app-owned network.
        assertEquals(TransferValidationResult.Valid, validate.validateExistentialDeposit(BigInteger.ONE, asset(solana),
            solana.id, SOLANA_RECIPIENT, SOLANA_SENDER, BigInteger.TEN).getOrThrow())
    }

    @Test
    fun `a Bitcoin recipient of the same network is valid and a Solana address is not`() = runBlocking {
        val validate = validation()
        val sender = BitcoinTransferServiceProviderTest.MAINNET_ADDRESS
        assertEquals(TransferValidationResult.Valid, validate.check(bitcoin, BitcoinTransferServiceProviderTest.MAINNET_RECIPIENT, sender))
        assertEquals(TransferValidationResult.InvalidAddress, validate.check(bitcoin, SOLANA_RECIPIENT, sender))
        // An uppercase bech32 address is the same address, so it is still a payment to the wallet itself.
        assertEquals(TransferValidationResult.TransferToTheSameAddress, validate.check(bitcoin, sender, sender))
        assertEquals(TransferValidationResult.TransferToTheSameAddress, validate.check(bitcoin, sender.uppercase(), sender))
        assertEquals(TransferValidationResult.TransferToTheSameAddress, validate.check(bitcoin, " ${sender.uppercase()} ", sender))
        // Solana keys are case-sensitive: a different case is a different (here invalid) address, never the sender.
        assertEquals(TransferValidationResult.InvalidAddress, validate.check(solana, SOLANA_SENDER.lowercase(), SOLANA_SENDER))
    }

    private suspend fun ValidateTransferUseCaseImpl.check(chain: Chain, recipient: String, sender: String) = validateTransfer(
        amountInPlanks = BigInteger.ONE, asset = asset(chain), destinationAddress = recipient, senderAddress = sender,
        fee = BigInteger.TEN, confirmedValidations = emptyList(), transferMyselfAvailable = false, skipEdValidation = false
    ).getOrThrow()

    private fun validation(): ValidateTransferUseCaseImpl {
        // Native assets have no existential deposit; an unknown one must not surface as a warning.
        val existentialDeposit = object : ExistentialDepositUseCase {
            override suspend fun invoke(chainAsset: jp.co.soramitsu.core.models.Asset): Result<BigInteger> =
                Result.failure(ExistentialDepositUnavailableException(chainAsset, IllegalStateException("no runtime")))
        }
        val chains = mockk<ChainsRepository> {
            coEvery { getChain(solana.id) } returns solana
            coEvery { getChain(bitcoin.id) } returns bitcoin
        }
        return ValidateTransferUseCaseImpl(existentialDeposit, mockk<WalletConstants>(), chains, mockk<AccountRepository>(),
            mockk<WalletRepository>(), mockk())
    }

    private fun asset(chain: Chain) = Asset(
        metaId = 1,
        token = Token(configuration = chain.assets.first(), fiatRate = null, fiatSymbol = null, recentRateChange = null),
        accountId = ByteArray(32),
        freeInPlanks = BigInteger.ZERO,
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

    private companion object {
        const val SOLANA_SENDER = BitcoinTransferServiceProviderTest.SOLANA_ADDRESS
        val SOLANA_RECIPIENT = BitcoinTransferServiceProviderTest.SOLANA_RECIPIENT
        const val SUBSTRATE_ADDRESS = "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5"
    }
}
