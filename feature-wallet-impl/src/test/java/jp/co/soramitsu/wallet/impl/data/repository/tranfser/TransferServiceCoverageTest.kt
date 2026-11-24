package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferServiceCoverageTest {

    private val substrateSource = mockk<SubstrateRemoteSource>()
    private val ethereumSource = mockk<EthereumRemoteSource>()
    private val accountRepository = mockk<AccountRepository>()

    @Test
    fun `substrate fee uses the utility precision and is observed once`(): Unit = runBlocking {
        val chain = WalletTestModels.chain()
        val transfer = substrateTransfer(chain.assets.single())
        coEvery { substrateSource.getTransferFee(chain, transfer, null, false) } returns BigInteger.valueOf(1_500_000_000)
        val service = SubstrateTransferService(chain, substrateSource)

        assertEquals(BigDecimal("0.1500000000"), service.getTransferFee(transfer))
        assertEquals(listOf(BigDecimal("0.1500000000")), service.observeTransferFee(transfer).toList())
        coVerify(exactly = 2) { substrateSource.getTransferFee(chain, transfer, null, false) }
    }

    @Test
    fun `substrate fee needs a utility asset and substrate sends have no exact maximum`(): Unit = runBlocking {
        val asset = WalletTestModels.asset(isUtility = false)
        val chain = WalletTestModels.chain(assets = listOf(asset))
        val transfer = substrateTransfer(asset)
        coEvery { substrateSource.getTransferFee(chain, transfer, null, false) } returns BigInteger.valueOf(5)
        val service = SubstrateTransferService(chain, substrateSource)

        val feeError = runCatching { service.getTransferFee(transfer) }.exceptionOrNull()
        val maxError = runCatching { service.getTransferMaxAmount(transfer) }.exceptionOrNull()

        assertEquals("cannot calculate substrate fee Polkadot, fee: 5", feeError?.message)
        assertTrue(maxError is UnsupportedOperationException)
    }

    @Test
    fun `ethereum fee follows the gas price flow`(): Unit = runBlocking {
        val chain = ethereumChain(isUtility = true)
        val transfer = ethereumTransfer(chain.assets.single())
        every { ethereumSource.listenGas(transfer, chain) } returns flowOf(BigInteger("21000000000000"), BigInteger("42000000000000"))
        val service = EthereumTransferService(chain, ethereumSource, mockk())

        assertEquals(BigDecimal("0.000021000000000000"), service.getTransferFee(transfer))
        assertEquals(
            listOf(BigDecimal("0.000021000000000000"), BigDecimal("0.000042000000000000")),
            service.observeTransferFee(transfer).toList()
        )
    }

    @Test
    fun `ethereum fee needs a utility asset`(): Unit = runBlocking {
        val chain = ethereumChain(isUtility = false)
        val transfer = ethereumTransfer(chain.assets.single())
        every { ethereumSource.listenGas(transfer, chain) } returns flowOf(BigInteger.ONE)

        val error = runCatching { EthereumTransferService(chain, ethereumSource, mockk()).getTransferFee(transfer) }.exceptionOrNull()

        assertEquals("cannot calculate ethereum fee Ethereum, fee: 1", error?.message)
    }

    @Test
    fun `iroha fees need an iroha network`(): Unit = runBlocking {
        val chain = WalletTestModels.chain()
        val transfer = Transfer(OWN_ADDRESS, OTHER_ADDRESS, BigDecimal.ONE, chain.assets.single())

        val error = runCatching { irohaService(chain).observeTransferFee(transfer).toList() }.exceptionOrNull()

        assertEquals("Iroha network is not configured for Polkadot", error?.message)
    }

    @Test
    fun `iroha transfers need an iroha account in the selected wallet`(): Unit = runBlocking {
        val chain = tairaChain(withHistory = false)
        coEvery { accountRepository.getSelectedMetaAccount() } returns metaAccount(chainAccounts = emptyMap())

        val error = runCatching { irohaService(chain).getTransferFee(irohaTransfer(chain)) }.exceptionOrNull()

        assertEquals("Selected wallet has no Iroha account for Taira", error?.message)
    }

    @Test
    fun `iroha transfers refuse invalid senders, foreign senders and invalid recipients`(): Unit = runBlocking {
        val chain = tairaChain(withHistory = true)
        stubIrohaAccount(chain)
        val service = irohaService(chain)

        assertEquals(
            "Iroha sender address is invalid for Taira",
            failure { service.getTransferFee(irohaTransfer(chain, sender = "not-an-address")) }
        )
        assertEquals(
            "Iroha sender does not match selected wallet for Taira",
            failure { service.getTransferFee(irohaTransfer(chain, sender = OTHER_ADDRESS)) }
        )
        assertEquals(
            "Iroha recipient address is invalid for Taira",
            failure { service.getTransferFee(irohaTransfer(chain, recipient = "not-an-address")) }
        )
    }

    @Test
    fun `iroha transfers refuse non positive amounts and malformed asset ids`(): Unit = runBlocking {
        val chain = tairaChain(withHistory = true)
        stubIrohaAccount(chain)
        val service = irohaService(chain)

        assertEquals(
            "Iroha transfer amount must be greater than zero for Taira",
            failure { service.getTransferFee(irohaTransfer(chain, amount = BigDecimal.ZERO)) }
        )
        assertEquals(
            "Iroha transfer amount is invalid for Taira",
            failure { service.getTransferFee(irohaTransfer(chain, amount = BigDecimal("-1"))) }
        )
        assertEquals(
            "Iroha asset definition id is invalid for Taira",
            failure { service.getTransferFee(irohaTransfer(chain, asset = chain.assets.single().copy(id = "xor"))) }
        )
    }

    @Test
    fun `iroha transfers need mnemonic root material`(): Unit = runBlocking {
        val chain = tairaChain(withHistory = true)
        stubIrohaAccount(chain)
        coEvery { accountRepository.getSubstrateSecrets(1L) } returns null
        coEvery { accountRepository.getEthereumSecrets(1L) } returns null
        coEvery { accountRepository.getTonSecrets(1L) } returns null

        val error = failure { irohaService(chain).getTransferFee(irohaTransfer(chain)) }

        assertEquals("Iroha transfers require mnemonic root material for Taira", error)
    }

    private suspend fun failure(block: suspend () -> Unit): String? = runCatching { block() }.exceptionOrNull()?.message

    private fun irohaService(chain: Chain) = IrohaTransferService(chain, accountRepository, mockk<IrohaToriiClient>(), mockk())

    private fun stubIrohaAccount(chain: Chain) {
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        coEvery { accountRepository.getSelectedMetaAccount() } returns metaAccount(
            chainAccounts = mapOf(
                chain.id to MetaAccount.ChainAccount(
                    metaId = 1,
                    chain = chain,
                    publicKey = account.publicKey,
                    accountId = account.publicKey,
                    cryptoType = CryptoType.ED25519,
                    accountName = "Iroha"
                )
            )
        )
    }

    private fun irohaTransfer(
        chain: Chain,
        sender: String = OWN_ADDRESS,
        recipient: String = OTHER_ADDRESS,
        amount: BigDecimal = BigDecimal("1.5"),
        asset: Asset = chain.assets.single()
    ) = Transfer(sender, recipient, amount, asset)

    private fun substrateTransfer(asset: Asset) = Transfer(WalletTestModels.ALICE, WalletTestModels.BOB, BigDecimal.ONE, asset)

    private fun ethereumTransfer(asset: Asset) = Transfer(WalletTestModels.ETH_A, WalletTestModels.ETH_B, BigDecimal.ONE, asset)

    private fun ethereumChain(isUtility: Boolean) = WalletTestModels.chain(
        id = "ethereum",
        ecosystem = Ecosystem.Ethereum,
        assets = listOf(WalletTestModels.asset(id = "eth", chainId = "ethereum", precision = 18, isUtility = isUtility))
    )

    private fun tairaChain(withHistory: Boolean): Chain {
        val network = UniversalWalletRegistry.taira
        val history = Chain.ExternalApi.Section(Chain.ExternalApi.Section.Type.IROHA, requireNotNull(network.toriiBaseUrl))
        return WalletTestModels.chain(
            id = network.id,
            name = "Taira",
            isTestNet = true,
            externalApi = if (withHistory) Chain.ExternalApi(staking = null, history = history, crowdloans = null) else null,
            assets = listOf(WalletTestModels.asset(id = "xor#sora", chainId = network.id, precision = 18))
        )
    }

    private fun metaAccount(chainAccounts: Map<String, MetaAccount.ChainAccount>) = MetaAccount(
        id = 1,
        chainAccounts = chainAccounts,
        favoriteChains = emptyMap(),
        substratePublicKey = null,
        substrateCryptoType = null,
        substrateAccountId = null,
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
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val OTHER_MNEMONIC = "legal winner thank year wave sausage worth useful legal winner thank yellow"

        val OWN_ADDRESS: String by lazy {
            IrohaKeyDerivation.deriveAddress(mnemonic = MNEMONIC, chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant).i105
        }
        val OTHER_ADDRESS: String by lazy {
            IrohaKeyDerivation.deriveAddress(mnemonic = OTHER_MNEMONIC, chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant).i105
        }
    }
}
