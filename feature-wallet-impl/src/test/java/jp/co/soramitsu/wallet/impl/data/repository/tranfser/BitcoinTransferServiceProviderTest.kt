package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraStats
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTransaction
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTxStatus
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraUtxo
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAssetDefinitionListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcRequest
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaPipelineTransactionStatusResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes
import jp.co.soramitsu.common.data.network.iroha.IrohaTransactionSubmissionPayload
import jp.co.soramitsu.common.data.network.iroha.IrohaTransactionSubmissionReceipt
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSync
import jp.co.soramitsu.common.data.network.solana.SolanaBroadcastOptions
import jp.co.soramitsu.common.data.network.solana.SolanaFeeForMessageResponse
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerClient
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerServiceInfo
import jp.co.soramitsu.common.data.network.solana.SolanaLatestBlockhash
import jp.co.soramitsu.common.data.network.solana.SolanaLatestBlockhashResponse
import jp.co.soramitsu.common.data.network.solana.SolanaNativeBalance
import jp.co.soramitsu.common.data.network.solana.SolanaRpcClient
import jp.co.soramitsu.common.data.network.solana.SolanaRpcCommitment
import jp.co.soramitsu.common.data.network.solana.SolanaRpcContext
import jp.co.soramitsu.common.data.network.solana.SolanaSimulationOptions
import jp.co.soramitsu.common.data.network.solana.SolanaSimulationResponse
import jp.co.soramitsu.common.data.network.solana.SolanaSimulationValue
import jp.co.soramitsu.common.data.network.solana.SolanaTokenMetadata
import jp.co.soramitsu.common.data.network.solana.SolanaTokenMetadataBatchResponse
import jp.co.soramitsu.common.data.network.solana.SolanaTokenBalance
import jp.co.soramitsu.common.data.network.solana.SolanaTokenProgram
import jp.co.soramitsu.common.data.network.solana.SolanaTransferTransactionBuilder
import jp.co.soramitsu.common.data.network.solana.SolanaWalletAssetsResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletBalancesResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletStateResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletTransactionsResponse
import jp.co.soramitsu.common.data.network.ton.AccountStatus
import jp.co.soramitsu.common.data.network.ton.SendBlockchainMessageRequest
import jp.co.soramitsu.common.data.network.ton.TonAccountData
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.common.utils.IrohaAddressCodec
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.TonKeyDerivation
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.extrinsic.keypair_provider.SingleKeypairProvider
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.testshared.any
import jp.co.soramitsu.testshared.eq
import jp.co.soramitsu.testshared.whenever
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Base64

class BitcoinTransferServiceProviderTest {

    @Test
    fun `provider routes bitcoin chains to signed bitcoin transfer service`() {
        val chain = bitcoinChain()
        val client = FakeBitcoinIndexerClient(broadcastResponse = EXPECTED_TXID.uppercase())
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = bitcoinMetaAccount(chain),
                mnemonic = MNEMONIC
            ),
            bitcoinIndexerClient = client
        ).provide(chain)

        assertTrue(service is BitcoinTransferService)
        val fee = runBlocking {
            service.getTransferFee(Transfer(MAINNET_ADDRESS, MAINNET_RECIPIENT, BigDecimal("0.0005"), chain.assets.single()))
        }
        val txid = runBlocking {
            service.transfer(Transfer(MAINNET_ADDRESS, MAINNET_RECIPIENT, BigDecimal("0.0005"), chain.assets.single()))
        }

        assertEquals(BigDecimal("0.00000282"), fee)
        assertEquals(EXPECTED_TXID, txid)
        assertEquals(EXPECTED_TX_HEX, client.lastBroadcastTxHex)
        assertEquals(BitcoinIndexerRoutes.Network.Mainnet, client.lastBroadcastNetwork)
        assertEquals(UniversalWalletRegistry.BITCOIN_MAINNET_INDEXER_BASE_URL, client.lastBroadcastBaseUrl)
    }

    @Test
    fun `bitcoin transfer rejects sender mismatch and missing mnemonic root`() {
        val chain = bitcoinChain()
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = bitcoinMetaAccount(chain),
                mnemonic = MNEMONIC
            ),
            bitcoinIndexerClient = FakeBitcoinIndexerClient()
        ).provide(chain)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                service.getTransferFee(Transfer(MAINNET_RECIPIENT, MAINNET_RECIPIENT, BigDecimal("0.0005"), chain.assets.single()))
            }
        }

        val noSecretService = provider(
            accountRepository = accountRepository(
                metaAccount = bitcoinMetaAccount(chain),
                mnemonic = null
            ),
            bitcoinIndexerClient = FakeBitcoinIndexerClient()
        ).provide(chain)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                noSecretService.transfer(Transfer(MAINNET_ADDRESS, MAINNET_RECIPIENT, BigDecimal("0.0005"), chain.assets.single()))
            }
        }
    }

    @Test
    fun `bitcoin transfer rejects mnemonic mismatch before indexer calls`() {
        val chain = bitcoinChain()
        val client = FakeBitcoinIndexerClient()
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = bitcoinMetaAccount(chain),
                mnemonic = OTHER_MNEMONIC
            ),
            bitcoinIndexerClient = client
        ).provide(chain)

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                service.transfer(Transfer(MAINNET_ADDRESS, MAINNET_RECIPIENT, BigDecimal("0.0005"), chain.assets.single()))
            }
        }

        assertTrue(error.message!!.contains("does not match selected wallet"))
        assertEquals(0, client.feeEstimateCalls)
        assertEquals(0, client.utxoCalls)
        assertEquals(null, client.lastBroadcastTxHex)
    }

    @Test
    fun `bitcoin transfer rejects raw amounts outside long range before indexer calls`() {
        val chain = bitcoinChain()
        val asset = chain.assets.single()
        val client = FakeBitcoinIndexerClient()
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = bitcoinMetaAccount(chain),
                mnemonic = MNEMONIC
            ),
            bitcoinIndexerClient = client
        ).provide(chain)

        listOf(
            BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
            BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE)
        ).forEach { rawAmount ->
            val error = assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    service.getTransferFee(
                        Transfer(
                            MAINNET_ADDRESS,
                            MAINNET_RECIPIENT,
                            BigDecimal(rawAmount, asset.precision),
                            asset
                        )
                    )
                }
            }

            assertEquals("Bitcoin transfer amount is outside the supported satoshi range", error.message)
        }

        assertEquals(0, client.feeEstimateCalls)
        assertEquals(0, client.utxoCalls)
        assertEquals(null, client.lastBroadcastTxHex)
    }

    @Test
    fun `provider routes solana chains to signed native sol transfer service`() {
        val chain = solanaChain()
        val rpcClient = FakeSolanaRpcClient()
        val indexerClient = FakeSolanaIndexerClient()
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = solanaMetaAccount(chain),
                mnemonic = MNEMONIC
            ),
            solanaRpcClient = rpcClient,
            solanaBalanceSync = SolanaBalanceSync(indexerClient)
        ).provide(chain)

        assertTrue(service is SolanaTransferService)
        val fee = runBlocking {
            service.getTransferFee(Transfer(SOLANA_ADDRESS, SOLANA_RECIPIENT, BigDecimal("0.001"), chain.assets.single()))
        }
        val signature = runBlocking {
            service.transfer(Transfer(SOLANA_ADDRESS, SOLANA_RECIPIENT, BigDecimal("0.001"), chain.assets.single()))
        }

        assertEquals(0, BigDecimal("0.000005").compareTo(fee))
        assertEquals(rpcClient.lastExpectedSignature, signature)
        assertEquals(List(6) { UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL }, rpcClient.urls)
        assertEquals(SOLANA_ADDRESS, indexerClient.lastBalancesWallet)
        assertEquals(UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL, indexerClient.lastBalancesBaseUrl)
    }

    @Test
    fun `solana transfer sends standard spl token from indexed source account`() {
        val tokenAsset = solanaTokenAsset(UniversalWalletRegistry.solanaMainnet.id)
        val chain = solanaChain(
            assets = listOf(
                solanaAsset(UniversalWalletRegistry.solanaMainnet.id),
                tokenAsset
            )
        )
        val rpcClient = FakeSolanaRpcClient()
        val indexerClient = FakeSolanaIndexerClient(
            tokens = listOf(
                solanaTokenBalance()
            )
        )
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = solanaMetaAccount(chain),
                mnemonic = MNEMONIC
            ),
            solanaRpcClient = rpcClient,
            solanaBalanceSync = SolanaBalanceSync(indexerClient)
        ).provide(chain)
        val transfer = Transfer(SOLANA_ADDRESS, SOLANA_RECIPIENT, BigDecimal("1.25"), tokenAsset)

        val fee = runBlocking {
            service.getTransferFee(transfer)
        }
        val signature = runBlocking {
            service.transfer(transfer)
        }

        val destinationTokenAccount = SolanaTransferTransactionBuilder.deriveAssociatedTokenAccountAddress(
            walletAddress = SOLANA_RECIPIENT,
            mintAddress = SPL_TOKEN_MINT,
            tokenProgram = SolanaTokenProgram.SplToken
        )
        assertEquals(0, BigDecimal("0.000005").compareTo(fee))
        assertEquals(rpcClient.lastExpectedSignature, signature)
        assertEquals(List(2) { destinationTokenAccount }, rpcClient.accountExistenceChecks)
        assertEquals(SOLANA_ADDRESS, indexerClient.lastBalancesWallet)
        assertEquals(UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL, indexerClient.lastBalancesBaseUrl)
    }

    @Test
    fun `solana token transfer rejects token 2022 until fee policy is supported`() {
        val tokenAsset = solanaTokenAsset(UniversalWalletRegistry.solanaMainnet.id)
        val chain = solanaChain(
            assets = listOf(
                solanaAsset(UniversalWalletRegistry.solanaMainnet.id),
                tokenAsset
            )
        )
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = solanaMetaAccount(chain),
                mnemonic = MNEMONIC
            ),
            solanaRpcClient = FakeSolanaRpcClient(),
            solanaBalanceSync = SolanaBalanceSync(
                FakeSolanaIndexerClient(
                    tokens = listOf(
                        solanaTokenBalance(
                            program = "token-2022",
                            programId = TOKEN_2022_PROGRAM_ID
                        )
                    )
                )
            )
        ).provide(chain)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                service.getTransferFee(Transfer(SOLANA_ADDRESS, SOLANA_RECIPIENT, BigDecimal("1.25"), tokenAsset))
            }
        }
    }

    @Test
    fun `solana transfer uses devnet rpc for devnet registry chains`() {
        val chain = solanaChain(UniversalWalletRegistry.solanaDevnet)
        val rpcClient = FakeSolanaRpcClient()
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = solanaMetaAccount(chain),
                mnemonic = MNEMONIC
            ),
            solanaRpcClient = rpcClient
        ).provide(chain)

        runBlocking {
            service.getTransferFee(Transfer(SOLANA_ADDRESS, SOLANA_RECIPIENT, BigDecimal("0.001"), chain.assets.single()))
        }

        assertEquals(List(2) { UniversalWalletRegistry.SOLANA_DEVNET_RPC_URL }, rpcClient.urls)
    }

    @Test
    fun `solana transfer rejects sender mismatch and missing mnemonic root`() {
        val chain = solanaChain()
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = solanaMetaAccount(chain),
                mnemonic = MNEMONIC
            ),
            solanaRpcClient = FakeSolanaRpcClient()
        ).provide(chain)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                service.getTransferFee(Transfer(SOLANA_RECIPIENT, SOLANA_RECIPIENT, BigDecimal("0.001"), chain.assets.single()))
            }
        }

        val noSecretService = provider(
            accountRepository = accountRepository(
                metaAccount = solanaMetaAccount(chain),
                mnemonic = null
            ),
            solanaRpcClient = FakeSolanaRpcClient()
        ).provide(chain)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                noSecretService.transfer(Transfer(SOLANA_ADDRESS, SOLANA_RECIPIENT, BigDecimal("0.001"), chain.assets.single()))
            }
        }
    }

    @Test
    fun `solana transfer rejects mnemonic mismatch before rpc or indexer calls`() {
        val tokenAsset = solanaTokenAsset(UniversalWalletRegistry.solanaMainnet.id)
        val chain = solanaChain(
            assets = listOf(
                solanaAsset(UniversalWalletRegistry.solanaMainnet.id),
                tokenAsset
            )
        )
        val rpcClient = FakeSolanaRpcClient()
        val indexerClient = FakeSolanaIndexerClient(
            tokens = listOf(solanaTokenBalance())
        )
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = solanaMetaAccount(chain),
                mnemonic = OTHER_MNEMONIC
            ),
            solanaRpcClient = rpcClient,
            solanaBalanceSync = SolanaBalanceSync(indexerClient)
        ).provide(chain)

        val nativeError = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                service.transfer(Transfer(SOLANA_ADDRESS, SOLANA_RECIPIENT, BigDecimal("0.001"), chain.assets.first { it.isNative == true }))
            }
        }
        val tokenError = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                service.transfer(Transfer(SOLANA_ADDRESS, SOLANA_RECIPIENT, BigDecimal("1.25"), tokenAsset))
            }
        }

        assertTrue(nativeError.message!!.contains("does not match selected wallet"))
        assertTrue(tokenError.message!!.contains("does not match selected wallet"))
        assertTrue(rpcClient.urls.isEmpty())
        assertEquals(null, indexerClient.lastBalancesWallet)
        assertEquals(null, indexerClient.lastBalancesBaseUrl)
    }

    @Test
    fun `solana transfer rejects raw amounts outside long range before rpc calls`() {
        val chain = solanaChain()
        val asset = chain.assets.single()
        val rpcClient = FakeSolanaRpcClient()
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = solanaMetaAccount(chain),
                mnemonic = MNEMONIC
            ),
            solanaRpcClient = rpcClient
        ).provide(chain)

        listOf(
            BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
            BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE)
        ).forEach { rawAmount ->
            val error = assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    service.getTransferFee(
                        Transfer(
                            SOLANA_ADDRESS,
                            SOLANA_RECIPIENT,
                            BigDecimal(rawAmount, asset.precision),
                            asset
                        )
                    )
                }
            }

            assertEquals("Solana transfer amount is outside the supported lamport range", error.message)
        }

        assertTrue(rpcClient.urls.isEmpty())
        assertTrue(rpcClient.accountExistenceChecks.isEmpty())
    }

    @Test
    fun `provider routes iroha chains to fail closed iroha transfer service`() {
        val chain = irohaChain()
        val sourceAddress = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val recipientAddress = IrohaAddressCodec.encode(
            publicKeyHex = "11".repeat(32),
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        )
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = irohaMetaAccount(chain),
                mnemonic = MNEMONIC
            )
        ).provide(chain)

        assertTrue(service is IrohaTransferService)
        val fee = runBlocking {
            service.getTransferFee(Transfer(sourceAddress, recipientAddress, BigDecimal.ONE, chain.assets.single()))
        }
        assertEquals(BigDecimal.ZERO, fee)
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                service.transfer(Transfer(sourceAddress, recipientAddress, BigDecimal.ONE, chain.assets.single()))
            }
        }
    }

    @Test
    fun `iroha transfer builds signed transfer request and submits norito to torii`() {
        val chain = irohaChain()
        val signer = FakeIrohaTransferSigner()
        val toriiClient = FakeIrohaToriiClient()
        val sourceAddress = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val recipientAddress = IrohaAddressCodec.encode(
            publicKeyHex = "11".repeat(32),
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        )
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = irohaMetaAccount(chain),
                mnemonic = MNEMONIC
            ),
            irohaToriiClient = toriiClient,
            irohaTransferSigner = signer
        ).provide(chain)
        val transfer = Transfer(sourceAddress, recipientAddress, BigDecimal("1.25"), chain.assets.single())

        val fee = runBlocking {
            service.getTransferFee(transfer)
        }
        val hash = runBlocking {
            service.transfer(transfer)
        }

        assertEquals(BigDecimal.ZERO, fee)
        assertEquals(SIGNED_TRANSACTION_HASH, hash)
        assertEquals(SIGNED_TRANSACTION_BYTES.toList(), toriiClient.lastSubmittedNorito?.toList())
        assertEquals(UniversalWalletRegistry.taira.toriiBaseUrl, toriiClient.lastSubmitBaseUrl)
        assertEquals("1.25", signer.lastRequest?.amount)
        assertEquals("xor#sora", signer.lastRequest?.assetDefinitionId)
        assertEquals(sourceAddress, signer.lastRequest?.authority)
        assertEquals(UniversalWalletRegistry.taira.chainId, signer.lastRequest?.chainId)
        assertEquals("m/44'/617'/0'/0'", signer.lastRequest?.derivationPath)
        assertEquals(recipientAddress, signer.lastRequest?.destinationAccountId)
        assertEquals(MNEMONIC, signer.lastRequest?.mnemonicOrSeed)
        assertEquals("taira", signer.lastRequest?.network)
        assertEquals(IrohaAddressCodec.parse(sourceAddress, UniversalWalletRegistry.taira.chainDiscriminant).publicKeyHex, signer.lastRequest?.signingPublicKeyHex)
        assertEquals(sourceAddress, signer.lastRequest?.sourceAccountId)
        assertEquals("xor#sora#$sourceAddress", signer.lastRequest?.sourceAssetId)
        assertTrue(signer.lastRequest?.transactionMetadata?.isEmpty() == true)
    }

    @Test
    fun `iroha transfer builds nexus signed transfer request and submits norito to minamoto torii`() {
        val chain = irohaChain(UniversalWalletRegistry.nexus)
        val signer = FakeIrohaTransferSigner()
        val toriiClient = FakeIrohaToriiClient()
        val sourceAddress = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.nexus.chainDiscriminant
        ).i105
        val recipientAddress = IrohaAddressCodec.encode(
            publicKeyHex = "22".repeat(32),
            chainDiscriminant = UniversalWalletRegistry.nexus.chainDiscriminant
        )
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = irohaMetaAccount(chain),
                mnemonic = MNEMONIC
            ),
            irohaToriiClient = toriiClient,
            irohaTransferSigner = signer
        ).provide(chain)
        val transfer = Transfer(sourceAddress, recipientAddress, BigDecimal("1.25"), chain.assets.single())

        val fee = runBlocking {
            service.getTransferFee(transfer)
        }
        val hash = runBlocking {
            service.transfer(transfer)
        }

        assertEquals(BigDecimal.ZERO, fee)
        assertEquals(SIGNED_TRANSACTION_HASH, hash)
        assertEquals(SIGNED_TRANSACTION_BYTES.toList(), toriiClient.lastSubmittedNorito?.toList())
        assertEquals(UniversalWalletRegistry.nexus.toriiBaseUrl, toriiClient.lastSubmitBaseUrl)
        assertEquals("1.25", signer.lastRequest?.amount)
        assertEquals("xor#sora", signer.lastRequest?.assetDefinitionId)
        assertEquals(sourceAddress, signer.lastRequest?.authority)
        assertEquals(UniversalWalletRegistry.nexus.chainId, signer.lastRequest?.chainId)
        assertEquals("m/44'/617'/0'/0'", signer.lastRequest?.derivationPath)
        assertEquals(recipientAddress, signer.lastRequest?.destinationAccountId)
        assertEquals(MNEMONIC, signer.lastRequest?.mnemonicOrSeed)
        assertEquals("nexus", signer.lastRequest?.network)
        assertEquals(IrohaAddressCodec.parse(sourceAddress, UniversalWalletRegistry.nexus.chainDiscriminant).publicKeyHex, signer.lastRequest?.signingPublicKeyHex)
        assertEquals(sourceAddress, signer.lastRequest?.sourceAccountId)
        assertEquals("xor#sora#$sourceAddress", signer.lastRequest?.sourceAssetId)
        assertTrue(signer.lastRequest?.transactionMetadata?.isEmpty() == true)
    }

    @Test
    fun `nexus wallet smoke evidence uses exact immutable metadata and canonical minamoto`() {
        val chain = irohaChain(UniversalWalletRegistry.nexus)
        val signer = FakeIrohaTransferSigner()
        val toriiClient = FakeIrohaToriiClient()
        val sourceAddress = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.nexus.chainDiscriminant
        ).i105
        val recipientAddress = IrohaAddressCodec.encode(
            publicKeyHex = "22".repeat(32),
            chainDiscriminant = UniversalWalletRegistry.nexus.chainDiscriminant
        )
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = irohaMetaAccount(chain),
                mnemonic = MNEMONIC
            ),
            irohaToriiClient = toriiClient,
            irohaTransferSigner = signer
        ).provide(chain) as IrohaTransferService
        val transfer = Transfer(sourceAddress, recipientAddress, BigDecimal("1.25"), chain.assets.single())
        val supplied = walletSmokeMetadata()

        val hash = runBlocking {
            service.transferWalletSmokeEvidence(transfer, supplied)
        }
        supplied.clear()
        supplied["attacker"] = "injected"

        assertEquals(SIGNED_TRANSACTION_HASH, hash)
        assertEquals(
            linkedMapOf(
                "evidence_role" to "wallet-smoke",
                "route_governance_action_hash" to WALLET_SMOKE_ROUTE_HASH,
                "wallet_platform" to "android",
                "wallet_commit" to WALLET_SMOKE_COMMIT
            ),
            signer.lastRequest?.transactionMetadata?.asStringMap()
        )
        assertEquals("nexus", signer.lastRequest?.network)
        assertEquals("sora:nexus:global", signer.lastRequest?.chainId)
        assertEquals(UniversalWalletRegistry.nexus.toriiBaseUrl, toriiClient.lastSubmitBaseUrl)
        assertEquals(SIGNED_TRANSACTION_BYTES.toList(), toriiClient.lastSubmittedNorito?.toList())
    }

    @Test
    fun `wallet smoke evidence rejects taira and noncanonical minamoto before signer or torii`() {
        val taira = irohaChain()
        val tairaSigner = FakeIrohaTransferSigner()
        val tairaTorii = FakeIrohaToriiClient()
        val tairaAccountRepository = accountRepository(irohaMetaAccount(taira), MNEMONIC)
        val tairaSource = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val tairaRecipient = IrohaAddressCodec.encode(
            publicKeyHex = "11".repeat(32),
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        )
        val tairaService = provider(
            accountRepository = tairaAccountRepository,
            irohaToriiClient = tairaTorii,
            irohaTransferSigner = tairaSigner
        ).provide(taira) as IrohaTransferService

        val tairaError = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                tairaService.transferWalletSmokeEvidence(
                    Transfer(tairaSource, tairaRecipient, BigDecimal.ONE, taira.assets.single()),
                    walletSmokeMetadata()
                )
            }
        }
        assertTrue(tairaError.message.orEmpty().contains("requires SORA Nexus"))
        verifyNoInteractions(tairaAccountRepository)
        assertEquals(null, tairaSigner.lastRequest)
        assertEquals(null, tairaTorii.lastSubmittedNorito)

        val nexus = irohaChain(UniversalWalletRegistry.nexus)
        val noncanonicalNexus = nexus.copy(
            externalApi = nexus.externalApi!!.copy(
                history = Chain.ExternalApi.Section(
                    Chain.ExternalApi.Section.Type.IROHA,
                    "https://attacker.invalid"
                )
            )
        )
        val nexusSigner = FakeIrohaTransferSigner()
        val nexusTorii = FakeIrohaToriiClient()
        val nexusAccountRepository = accountRepository(irohaMetaAccount(noncanonicalNexus), MNEMONIC)
        val nexusSource = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.nexus.chainDiscriminant
        ).i105
        val nexusRecipient = IrohaAddressCodec.encode(
            publicKeyHex = "22".repeat(32),
            chainDiscriminant = UniversalWalletRegistry.nexus.chainDiscriminant
        )
        val nexusService = provider(
            accountRepository = nexusAccountRepository,
            irohaToriiClient = nexusTorii,
            irohaTransferSigner = nexusSigner
        ).provide(noncanonicalNexus) as IrohaTransferService

        val endpointError = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                nexusService.transferWalletSmokeEvidence(
                    Transfer(
                        nexusSource,
                        nexusRecipient,
                        BigDecimal.ONE,
                        noncanonicalNexus.assets.single()
                    ),
                    walletSmokeMetadata()
                )
            }
        }
        assertTrue(endpointError.message.orEmpty().contains("canonical SORA Nexus Minamoto"))
        verifyNoInteractions(nexusAccountRepository)
        assertEquals(null, nexusSigner.lastRequest)
        assertEquals(null, nexusTorii.lastSubmittedNorito)
    }

    @Test
    fun `wallet smoke evidence rejects malformed and aliasing metadata before signer or torii`() {
        val chain = irohaChain(UniversalWalletRegistry.nexus)
        val accountRepository = mock(AccountRepository::class.java)
        val signer = FakeIrohaTransferSigner()
        val toriiClient = FakeIrohaToriiClient()
        val service = provider(
            accountRepository = accountRepository,
            irohaToriiClient = toriiClient,
            irohaTransferSigner = signer
        ).provide(chain) as IrohaTransferService
        val transfer = Transfer("invalid sender", "invalid recipient", BigDecimal.ONE, chain.assets.single())
        val invalid = listOf<Map<*, *>>(
            walletSmokeMetadata().apply { remove("evidence_role") },
            walletSmokeMetadata().apply { put("unexpected", "value") },
            walletSmokeMetadata().apply {
                remove("evidence_role")
                put("Evidence_role", "wallet-smoke")
            },
            walletSmokeMetadata().apply { put("evidence_role", "Wallet-Smoke") },
            walletSmokeMetadata().apply { put("route_governance_action_hash", "SHA256:${"a".repeat(64)}") },
            walletSmokeMetadata().apply { put("route_governance_action_hash", "sha256:${"0".repeat(64)}") },
            walletSmokeMetadata().apply { put("wallet_platform", "Android") },
            walletSmokeMetadata().apply { put("wallet_commit", "A".repeat(40)) },
            LinkedHashMap<Any?, Any?>().apply {
                putAll(walletSmokeMetadata())
                put("wallet_commit", 7)
            },
            LinkedHashMap<Any?, Any?>().apply {
                putAll(walletSmokeMetadata())
                put("wallet_commit", null)
            },
            LinkedHashMap<Any?, Any?>().apply {
                putAll(walletSmokeMetadata())
                put(null, "value")
            }
        )

        invalid.forEach { metadata ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { service.transferWalletSmokeEvidence(transfer, metadata) }
            }
        }

        verifyNoInteractions(accountRepository)
        assertEquals(null, signer.lastRequest)
        assertEquals(null, toriiClient.lastSubmittedNorito)
    }

    @Test
    fun `iroha transfer rejects mnemonic mismatch before signer or torii calls`() {
        val chain = irohaChain()
        val signer = FakeIrohaTransferSigner()
        val toriiClient = FakeIrohaToriiClient()
        val sourceAddress = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val recipientAddress = IrohaAddressCodec.encode(
            publicKeyHex = "11".repeat(32),
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        )
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = irohaMetaAccount(chain),
                mnemonic = OTHER_MNEMONIC
            ),
            irohaToriiClient = toriiClient,
            irohaTransferSigner = signer
        ).provide(chain)
        val transfer = Transfer(sourceAddress, recipientAddress, BigDecimal.ONE, chain.assets.single())

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                service.transfer(transfer)
            }
        }

        assertTrue(error.message!!.contains("does not match selected wallet"))
        assertEquals(null, signer.lastRequest)
        assertEquals(null, toriiClient.lastSubmittedNorito)
    }

    @Test
    fun `provider routes ton chains to ton transfer service`() {
        val service = provider().provide(tonChain())

        assertTrue(service is TonTransferService)
    }

    @Test
    fun `ton transfer signs and sends blockchain message`() {
        val chain = tonChain()
        val ton = TonKeyDerivation.deriveAccount(MNEMONIC)
        val tonRemoteSource = mock(TonRemoteSource::class.java)
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = tonMetaAccount(chain),
                mnemonic = MNEMONIC
            ),
            keyPairProvider = SingleKeypairProvider(
                keypair = BaseKeypair(ton.privateKey, ton.publicKey),
                cryptoType = CryptoType.ED25519
            ),
            tonRemoteSource = tonRemoteSource
        ).provide(chain)
        val transfer = Transfer(ton.addressNonBounceable, ton.addressNonBounceable, BigDecimal("0.05"), chain.assets.single())
        var sentRequest: SendBlockchainMessageRequest? = null

        runBlocking {
            whenever(tonRemoteSource.getSeqno(chain, ton.accountId)).thenReturn(7)
            whenever(tonRemoteSource.getRawTime(chain, ton.accountId)).thenReturn(1_700_000_000)
            whenever(tonRemoteSource.loadAccountData(chain, ton.addressNonBounceable)).thenReturn(tonActiveAccount(ton.accountId))
            whenever(tonRemoteSource.sendBlockchainMessage(eq(chain), any())).thenAnswer { invocation ->
                sentRequest = invocation.getArgument(1)
                "{\"ok\":true}"
            }
        }

        val hash = runBlocking {
            service.transfer(transfer)
        }

        assertTrue(hash.matches(Regex("[0-9a-fA-F]{64}")))
        assertTrue(sentRequest?.boc?.isNotBlank() == true)
        assertEquals(null, sentRequest?.batch)
    }

    @Test
    fun `ton transfer rejects missing ton key before remote calls`() {
        val chain = tonChain()
        val tonRemoteSource = mock(TonRemoteSource::class.java)
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = bitcoinMetaAccount(bitcoinChain()),
                mnemonic = MNEMONIC
            ),
            tonRemoteSource = tonRemoteSource
        ).provide(chain)
        val transfer = Transfer(TON_ADDRESS, TON_RECIPIENT, BigDecimal("0.05"), chain.assets.single())

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                service.getTransferFee(transfer)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                service.transfer(transfer)
            }
        }
        verifyNoInteractions(tonRemoteSource)
    }

    @Test
    fun `ton transfer rejects sender mismatch before remote calls`() {
        val chain = tonChain()
        val ton = TonKeyDerivation.deriveAccount(MNEMONIC)
        val tonRemoteSource = mock(TonRemoteSource::class.java)
        val service = provider(
            accountRepository = accountRepository(
                metaAccount = tonMetaAccount(chain),
                mnemonic = MNEMONIC
            ),
            keyPairProvider = SingleKeypairProvider(
                keypair = BaseKeypair(ton.privateKey, ton.publicKey),
                cryptoType = CryptoType.ED25519
            ),
            tonRemoteSource = tonRemoteSource
        ).provide(chain)
        val transfer = Transfer(MAINNET_ADDRESS, ton.addressNonBounceable, BigDecimal("0.05"), chain.assets.single())

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.getTransferFee(transfer)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.transfer(transfer)
            }
        }
        verifyNoInteractions(tonRemoteSource)
    }

    private fun provider(
        accountRepository: AccountRepository = mock(AccountRepository::class.java),
        keyPairProvider: KeypairProvider = mock(KeypairProvider::class.java),
        bitcoinIndexerClient: BitcoinIndexerClient = FakeBitcoinIndexerClient(),
        solanaRpcClient: SolanaRpcClient = FakeSolanaRpcClient(),
        solanaBalanceSync: SolanaBalanceSync = SolanaBalanceSync(FakeSolanaIndexerClient()),
        irohaToriiClient: IrohaToriiClient = FakeIrohaToriiClient(),
        irohaTransferSigner: IrohaTransferSigner = UnavailableIrohaTransferSigner,
        tonRemoteSource: TonRemoteSource = mock(TonRemoteSource::class.java)
    ): TransferServiceProvider {
        return TransferServiceProvider(
            substrateSource = mock(SubstrateRemoteSource::class.java),
            ethereumRemoteSource = mock(EthereumRemoteSource::class.java),
            keyPairRepository = keyPairProvider,
            accountRepository = accountRepository,
            tonRemoteSource = tonRemoteSource,
            assetDao = mock(AssetDao::class.java),
            bitcoinIndexerClient = bitcoinIndexerClient,
            solanaRpcClient = solanaRpcClient,
            solanaBalanceSync = solanaBalanceSync,
            irohaToriiClient = irohaToriiClient,
            irohaTransferSigner = irohaTransferSigner
        )
    }

    private fun accountRepository(
        metaAccount: MetaAccount,
        mnemonic: String?
    ): AccountRepository {
        val accountRepository = mock(AccountRepository::class.java)

        runBlocking {
            `when`(accountRepository.getSelectedMetaAccount()).thenReturn(metaAccount)
            `when`(accountRepository.getSubstrateSecrets(metaAccount.id)).thenReturn(
                mnemonic?.let {
                    SubstrateSecrets(
                        substrateKeyPair = BaseKeypair(ByteArray(32), ByteArray(32)),
                        entropy = MnemonicCreator.fromWords(it).entropy
                    )
                }
            )
            `when`(accountRepository.getEthereumSecrets(metaAccount.id)).thenReturn(null)
            `when`(accountRepository.getTonSecrets(metaAccount.id)).thenReturn(null)
        }

        return accountRepository
    }

    private class FakeBitcoinIndexerClient(
        private val broadcastResponse: String = EXPECTED_TXID
    ) : BitcoinIndexerClient {
        var lastBroadcastTxHex: String? = null
            private set
        var lastBroadcastNetwork: BitcoinIndexerRoutes.Network? = null
            private set
        var lastBroadcastBaseUrl: String? = null
            private set
        var feeEstimateCalls = 0
            private set
        var utxoCalls = 0
            private set

        override suspend fun address(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): BitcoinEsploraAddress {
            return BitcoinEsploraAddress(
                address = address,
                chainStats = BitcoinEsploraStats(0, 0, 0, 0, 0),
                mempoolStats = BitcoinEsploraStats(0, 0, 0, 0, 0)
            )
        }

        override suspend fun utxos(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): List<BitcoinEsploraUtxo> {
            utxoCalls += 1
            return listOf(
                BitcoinEsploraUtxo(
                    txid = TXID,
                    vout = 1,
                    value = 100_000,
                    status = BitcoinEsploraTxStatus(confirmed = true)
                )
            )
        }

        override suspend fun transactions(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?,
            lastSeenTxid: String?,
            mempool: Boolean
        ): List<BitcoinEsploraTransaction> = emptyList()

        override suspend fun feeEstimates(
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): Map<String, Double> {
            feeEstimateCalls += 1
            return mapOf("2" to 2.0)
        }

        override suspend fun broadcastTransaction(
            txHex: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): String {
            lastBroadcastTxHex = txHex
            lastBroadcastNetwork = network
            lastBroadcastBaseUrl = baseUrl

            return broadcastResponse
        }
    }

    private class FakeSolanaRpcClient(
        private val fee: Long? = 5_000L,
        private val simulationError: String? = null,
        private val existingAccounts: Set<String> = emptySet()
    ) : SolanaRpcClient {
        val urls = mutableListOf<String?>()
        val accountExistenceChecks = mutableListOf<String>()
        var lastExpectedSignature: String? = null
            private set

        override suspend fun latestBlockhash(
            commitment: SolanaRpcCommitment,
            rpcUrl: String?
        ): SolanaLatestBlockhashResponse {
            urls += rpcUrl
            return SolanaLatestBlockhashResponse(
                context = SolanaRpcContext(slot = 1),
                value = SolanaLatestBlockhash(blockhash = SOLANA_BLOCKHASH, lastValidBlockHeight = 99)
            )
        }

        override suspend fun feeForMessage(
            messageBase64: String,
            commitment: SolanaRpcCommitment,
            rpcUrl: String?
        ): SolanaFeeForMessageResponse {
            urls += rpcUrl
            return SolanaFeeForMessageResponse(SolanaRpcContext(slot = 2), fee)
        }

        override suspend fun minimumBalanceForRentExemption(
            dataLength: Int,
            commitment: SolanaRpcCommitment,
            rpcUrl: String?
        ): Long {
            urls += rpcUrl
            return 2_039_280L
        }

        override suspend fun accountExists(
            address: String,
            commitment: SolanaRpcCommitment,
            rpcUrl: String?
        ): Boolean {
            urls += rpcUrl
            accountExistenceChecks += address
            return address in existingAccounts
        }

        override suspend fun simulateTransaction(
            transactionBase64: String,
            options: SolanaSimulationOptions,
            rpcUrl: String?
        ): SolanaSimulationResponse {
            urls += rpcUrl
            return SolanaSimulationResponse(
                context = SolanaRpcContext(slot = 3),
                value = SolanaSimulationValue(
                    errorJson = simulationError,
                    logs = listOf("Program log: ok"),
                    replacementBlockhash = null,
                    unitsConsumed = 42
                )
            )
        }

        override suspend fun sendRawTransaction(
            transactionBase64: String,
            options: SolanaBroadcastOptions,
            rpcUrl: String?
        ): String {
            urls += rpcUrl
            lastExpectedSignature = firstSignature(transactionBase64)
            return lastExpectedSignature!!
        }
    }

    private class FakeSolanaIndexerClient(
        private val tokens: List<SolanaTokenBalance> = emptyList()
    ) : SolanaIndexerClient {
        var lastBalancesWallet: String? = null
            private set
        var lastBalancesBaseUrl: String? = null
            private set

        override suspend fun serviceInfo(baseUrl: String?): SolanaIndexerServiceInfo {
            return SolanaIndexerServiceInfo(
                schemaVersion = 1,
                serviceId = "si.soramitsu.io",
                serviceName = "Soramitsu Solana Indexer",
                ecosystem = "solana",
                chainId = "solana:mainnet",
                network = "mainnet-beta",
                publicBaseUrl = UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL,
                readOnly = true
            )
        }

        override suspend fun verifyServiceInfo(baseUrl: String?): SolanaIndexerServiceInfo {
            return serviceInfo(baseUrl)
        }

        override suspend fun balances(wallet: String, baseUrl: String?): SolanaWalletBalancesResponse {
            lastBalancesWallet = wallet
            lastBalancesBaseUrl = baseUrl
            return SolanaWalletBalancesResponse(
                wallet = wallet,
                native = SolanaNativeBalance(
                    lamports = "10000000000",
                    uiAmountString = "10"
                ),
                tokens = tokens,
                total = 1 + tokens.size,
                syncedAt = 1L
            )
        }

        override suspend fun assets(wallet: String, baseUrl: String?): SolanaWalletAssetsResponse {
            return SolanaWalletAssetsResponse(wallet = wallet, total = 0, syncedAt = 1L)
        }

        override suspend fun state(wallet: String, baseUrl: String?): SolanaWalletStateResponse {
            return SolanaWalletStateResponse(
                wallet = wallet,
                exists = true,
                lamports = "10000000000",
                executable = false,
                dataLength = 0,
                syncedAt = 1L
            )
        }

        override suspend fun transactions(
            wallet: String,
            baseUrl: String?,
            before: String?,
            limit: Int
        ): SolanaWalletTransactionsResponse {
            return SolanaWalletTransactionsResponse(
                wallet = wallet,
                limit = limit,
                total = 0,
                syncedAt = 1L
            )
        }

        override suspend fun tokenMetadata(mint: String, baseUrl: String?): SolanaTokenMetadata {
            return SolanaTokenMetadata(
                mint = mint,
                exists = false,
                program = "spl-token",
                syncedAt = 1L
            )
        }

        override suspend fun tokenMetadataBatch(
            mints: List<String>,
            baseUrl: String?
        ): SolanaTokenMetadataBatchResponse {
            return SolanaTokenMetadataBatchResponse(total = 0, syncedAt = 1L)
        }
    }

    private class FakeIrohaTransferSigner : IrohaTransferSigner {
        var lastRequest: IrohaTransferSigningRequest? = null
            private set

        override suspend fun buildAndSignTransfer(request: IrohaTransferSigningRequest): IrohaSignedTransfer {
            lastRequest = request

            return IrohaSignedTransfer(
                signedTransaction = SIGNED_TRANSACTION_BYTES,
                transactionHashHex = SIGNED_TRANSACTION_HASH
            )
        }
    }

    private class FakeIrohaToriiClient : IrohaToriiClient {
        var lastSubmittedNorito: ByteArray? = null
            private set
        var lastSubmitBaseUrl: String? = null
            private set

        override suspend fun health(baseUrl: String?): String = "ok"

        override suspend fun accounts(
            baseUrl: String?,
            limit: Int?,
            offset: Long?,
            countMode: IrohaToriiRoutes.CountMode?
        ): IrohaAccountListResponse = error("Unexpected Iroha accounts call")

        override suspend fun account(
            accountId: String,
            baseUrl: String?,
            network: UniversalWalletRegistry.IrohaNetwork
        ): IrohaAccountListItem = error("Unexpected Iroha account call")

        override suspend fun accountAssets(
            accountId: String,
            baseUrl: String?,
            limit: Int?,
            offset: Long?,
            countMode: IrohaToriiRoutes.CountMode?,
            asset: String?,
            scope: String?,
            network: UniversalWalletRegistry.IrohaNetwork
        ): IrohaAccountAssetListResponse = error("Unexpected Iroha account-assets call")

        override suspend fun assetDefinitions(baseUrl: String?): IrohaAssetDefinitionListResponse {
            error("Unexpected Iroha asset-definitions call")
        }

        override suspend fun submitTransaction(
            noritoBytes: ByteArray,
            baseUrl: String?
        ): IrohaTransactionSubmissionReceipt {
            lastSubmittedNorito = noritoBytes
            lastSubmitBaseUrl = baseUrl

            return IrohaTransactionSubmissionReceipt(
                payload = IrohaTransactionSubmissionPayload(
                    txHash = RECEIPT_TX_HASH,
                    entrypointHash = RECEIPT_ENTRYPOINT_HASH,
                    signedTransactionHash = RECEIPT_SIGNED_TRANSACTION_HASH,
                    submittedAtMs = 1L,
                    submittedAtHeight = 2L
                )
            )
        }

        override suspend fun transactionStatus(
            hash: String,
            baseUrl: String?,
            scope: IrohaToriiRoutes.TransactionStatusScope
        ): IrohaPipelineTransactionStatusResponse = error("Unexpected Iroha transaction-status call")

        override suspend fun mcpCapabilities(
            network: UniversalWalletRegistry.IrohaNetwork,
            baseUrl: String?
        ): Map<String, Any?> = error("Unexpected Iroha mcp capabilities call")

        override suspend fun mcpJsonRpc(
            request: IrohaMcpJsonRpcRequest,
            network: UniversalWalletRegistry.IrohaNetwork,
            baseUrl: String?
        ): IrohaMcpJsonRpcResponse = error("Unexpected Iroha mcp call")
    }

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val OTHER_MNEMONIC = "legal winner thank year wave sausage worth useful legal winner thank yellow"
        const val MAINNET_ADDRESS = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
        const val MAINNET_RECIPIENT = "bc1qslk39wvggqa0vl8nd6jckaz54dw3vk45c5w60m"
        const val SOLANA_ADDRESS = "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk"
        const val SOLANA_RECIPIENT = "So11111111111111111111111111111111111111112"
        const val TON_ADDRESS = "UQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAJKZ"
        const val TON_RECIPIENT = "UQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAJKZ"
        const val SOLANA_BLOCKHASH = "7GjNiPun3AzEazTZoFEjZgcBMeuaXdpjHq2raZTmTrfs"
        const val SPL_TOKEN_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val SPL_TOKEN_SOURCE_ACCOUNT = "9xQeWvG816bUx9EPfFny7R5AySPGaWWSLS9eSg9wAYjr"
        const val SPL_TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        const val TOKEN_2022_PROGRAM_ID = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBMeiDFFbmqS"
        const val IROHA_ADDRESS = "test-i105-address"
        val TXID = "11".repeat(32)
        val SIGNED_TRANSACTION_BYTES = byteArrayOf(1, 2, 3, 4)
        const val SIGNED_TRANSACTION_HASH = "signed-transaction-hash"
        const val RECEIPT_TX_HASH = "receipt-tx-hash"
        const val RECEIPT_ENTRYPOINT_HASH = "receipt-entrypoint-hash"
        const val RECEIPT_SIGNED_TRANSACTION_HASH = "receipt-signed-transaction-hash"
        const val WALLET_SMOKE_ROUTE_HASH =
            "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val WALLET_SMOKE_COMMIT = "0123456789abcdef0123456789abcdef01234567"
        const val EXPECTED_TXID = "94c9b9d5070f24e06725b1000d9b1a0d46473d07b59088aca35e3d3da345023d"
        const val EXPECTED_TX_HEX = "0200000000010111111111111111111111111111111111111111111111111111111111111111110100000000ffffffff0250c300000000000016001487ed12b988403af67cf36ea58b7454ab5d165ab436c2000000000000160014c0cebcd6c3d3ca8c75dc5ec62ebe55330ef910e202473044022009ec0c24a20c4346c6516065723e2e83e6a7e4dd278fb66e27f36d9108d7ef4c022077f115bfbd68a2bc7a4c100766d9cceaf5300bcfcdc775be0248e7fb5a58216801210330d54fd0dd420a6e5f8d3624f5f3482cae350f79d5f0753bf5beef9c2d91af3c00000000"
        private val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()

        fun bitcoinMetaAccount(chain: Chain): MetaAccount {
            val bitcoin = BitcoinKeyDerivation.deriveAccount(MNEMONIC)

            return MetaAccount(
                id = 1L,
                chainAccounts = mapOf(
                    chain.id to MetaAccount.ChainAccount(
                        metaId = 1L,
                        chain = chain,
                        publicKey = bitcoin.publicKey,
                        accountId = bitcoin.publicKey,
                        cryptoType = CryptoType.ECDSA,
                        accountName = "Bitcoin"
                    )
                ),
                favoriteChains = emptyMap(),
                substratePublicKey = ByteArray(32),
                substrateCryptoType = CryptoType.SR25519,
                substrateAccountId = ByteArray(32),
                ethereumAddress = null,
                ethereumPublicKey = null,
                tonPublicKey = null,
                isSelected = true,
                isBackedUp = true,
                googleBackupAddress = null,
                name = "Wallet",
                initialized = true
            )
        }

        fun solanaMetaAccount(chain: Chain): MetaAccount {
            val solana = SolanaKeyDerivation.deriveAccount(MNEMONIC)

            return MetaAccount(
                id = 1L,
                chainAccounts = mapOf(
                    chain.id to MetaAccount.ChainAccount(
                        metaId = 1L,
                        chain = chain,
                        publicKey = solana.publicKey,
                        accountId = solana.publicKey,
                        cryptoType = CryptoType.ED25519,
                        accountName = "Solana"
                    )
                ),
                favoriteChains = emptyMap(),
                substratePublicKey = ByteArray(32),
                substrateCryptoType = CryptoType.SR25519,
                substrateAccountId = ByteArray(32),
                ethereumAddress = null,
                ethereumPublicKey = null,
                tonPublicKey = null,
                isSelected = true,
                isBackedUp = true,
                googleBackupAddress = null,
                name = "Wallet",
                initialized = true
            )
        }

        fun irohaMetaAccount(chain: Chain): MetaAccount {
            val iroha = IrohaKeyDerivation.deriveAccount(MNEMONIC)

            return MetaAccount(
                id = 1L,
                chainAccounts = mapOf(
                    chain.id to MetaAccount.ChainAccount(
                        metaId = 1L,
                        chain = chain,
                        publicKey = iroha.publicKey,
                        accountId = iroha.publicKey,
                        cryptoType = CryptoType.ED25519,
                        accountName = "Iroha"
                    )
                ),
                favoriteChains = emptyMap(),
                substratePublicKey = ByteArray(32),
                substrateCryptoType = CryptoType.SR25519,
                substrateAccountId = ByteArray(32),
                ethereumAddress = null,
                ethereumPublicKey = null,
                tonPublicKey = null,
                isSelected = true,
                isBackedUp = true,
                googleBackupAddress = null,
                name = "Wallet",
                initialized = true
            )
        }

        fun tonMetaAccount(chain: Chain): MetaAccount {
            val ton = TonKeyDerivation.deriveAccount(MNEMONIC)

            return MetaAccount(
                id = 1L,
                chainAccounts = mapOf(
                    chain.id to MetaAccount.ChainAccount(
                        metaId = 1L,
                        chain = chain,
                        publicKey = ton.publicKey,
                        accountId = ton.publicKey,
                        cryptoType = CryptoType.ED25519,
                        accountName = "TON"
                    )
                ),
                favoriteChains = emptyMap(),
                substratePublicKey = ByteArray(32),
                substrateCryptoType = CryptoType.SR25519,
                substrateAccountId = ByteArray(32),
                ethereumAddress = null,
                ethereumPublicKey = null,
                tonPublicKey = ton.publicKey,
                isSelected = true,
                isBackedUp = true,
                googleBackupAddress = null,
                name = "Wallet",
                initialized = true
            )
        }

        fun tonActiveAccount(rawAddress: String): TonAccountData {
            return TonAccountData(
                address = rawAddress,
                balance = 1_000_000_000L,
                lastActivity = 1_700_000_000L,
                status = AccountStatus.active,
                getMethods = listOf("seqno"),
                isWallet = true
            )
        }

        fun firstSignature(transactionBase64: String): String {
            val decoded = Base64.getDecoder().decode(transactionBase64)
            return base58Encode(decoded.copyOfRange(1, 65))
        }

        fun base58Encode(bytes: ByteArray): String {
            var zeros = 0
            while (zeros < bytes.size && bytes[zeros].toInt() == 0) {
                zeros += 1
            }

            val encoded = ArrayList<Char>()
            val digits = bytes.copyOf()
            var start = zeros
            while (start < digits.size) {
                var remainder = 0
                for (i in start until digits.size) {
                    val value = (remainder shl 8) + (digits[i].toInt() and 0xff)
                    digits[i] = (value / 58).toByte()
                    remainder = value % 58
                }
                encoded.add(BASE58_ALPHABET[remainder])
                while (start < digits.size && digits[start].toInt() == 0) {
                    start += 1
                }
            }

            repeat(zeros) { encoded.add(BASE58_ALPHABET[0]) }
            return encoded.asReversed().joinToString("")
        }

        fun bitcoinChain(): Chain {
            val network = UniversalWalletRegistry.bitcoinMainnet

            return Chain(
                id = network.id,
                paraId = null,
                rank = null,
                name = network.name,
                minSupportedVersion = null,
                assets = listOf(bitcoinAsset(network.id)),
                nodes = emptyList(),
                explorers = emptyList(),
                externalApi = Chain.ExternalApi(
                    staking = null,
                    history = Chain.ExternalApi.Section(
                        Chain.ExternalApi.Section.Type.BITCOIN,
                        network.indexerBaseUrl
                    ),
                    crowdloans = null
                ),
                icon = "",
                addressPrefix = 0,
                isEthereumBased = false,
                isTestNet = false,
                hasCrowdloans = false,
                parentId = null,
                supportStakingPool = false,
                isEthereumChain = false,
                chainlinkProvider = false,
                supportNft = false,
                isUsesAppId = false,
                identityChain = null,
                ecosystem = Ecosystem.Substrate,
                remoteAssetsSource = null
            )
        }

        fun bitcoinAsset(chainId: String): Asset {
            return Asset(
                id = "BTC",
                name = "Bitcoin",
                symbol = "BTC",
                iconUrl = "",
                chainId = chainId,
                chainName = "Bitcoin",
                chainIcon = null,
                isTestNet = false,
                priceId = null,
                precision = 8,
                staking = Asset.StakingType.UNSUPPORTED,
                purchaseProviders = null,
                supportStakingPool = false,
                isUtility = true,
                type = ChainAssetType.Normal,
                currencyId = null,
                existentialDeposit = null,
                color = null,
                isNative = true
            )
        }

        fun solanaChain(
            network: UniversalWalletRegistry.SolanaNetwork = UniversalWalletRegistry.solanaMainnet,
            assets: List<Asset> = listOf(solanaAsset(network.id))
        ): Chain {
            return Chain(
                id = network.id,
                paraId = null,
                rank = null,
                name = network.name,
                minSupportedVersion = null,
                assets = assets,
                nodes = emptyList(),
                explorers = emptyList(),
                externalApi = Chain.ExternalApi(
                    staking = null,
                    history = Chain.ExternalApi.Section(
                        Chain.ExternalApi.Section.Type.SOLANA,
                        network.indexerBaseUrl
                    ),
                    crowdloans = null
                ),
                icon = "",
                addressPrefix = 0,
                isEthereumBased = false,
                isTestNet = network == UniversalWalletRegistry.solanaDevnet,
                hasCrowdloans = false,
                parentId = null,
                supportStakingPool = false,
                isEthereumChain = false,
                chainlinkProvider = false,
                supportNft = false,
                isUsesAppId = false,
                identityChain = null,
                ecosystem = Ecosystem.Substrate,
                remoteAssetsSource = null
            )
        }

        fun solanaAsset(chainId: String): Asset {
            return Asset(
                id = "SOL",
                name = "Solana",
                symbol = "SOL",
                iconUrl = "",
                chainId = chainId,
                chainName = "Solana",
                chainIcon = null,
                isTestNet = false,
                priceId = null,
                precision = 9,
                staking = Asset.StakingType.UNSUPPORTED,
                purchaseProviders = null,
                supportStakingPool = false,
                isUtility = true,
                type = ChainAssetType.Normal,
                currencyId = null,
                existentialDeposit = null,
                color = null,
                isNative = true
            )
        }

        fun solanaTokenAsset(chainId: String): Asset {
            return Asset(
                id = "USDC",
                name = "USD Coin",
                symbol = "USDC",
                iconUrl = "",
                chainId = chainId,
                chainName = "Solana",
                chainIcon = null,
                isTestNet = false,
                priceId = null,
                precision = 6,
                staking = Asset.StakingType.UNSUPPORTED,
                purchaseProviders = null,
                supportStakingPool = false,
                isUtility = false,
                type = ChainAssetType.Normal,
                currencyId = SPL_TOKEN_MINT,
                existentialDeposit = null,
                color = null,
                isNative = false
            )
        }

        fun solanaTokenBalance(
            program: String = "spl-token",
            programId: String = SPL_TOKEN_PROGRAM_ID
        ): SolanaTokenBalance {
            return SolanaTokenBalance(
                accountAddress = SPL_TOKEN_SOURCE_ACCOUNT,
                mint = SPL_TOKEN_MINT,
                owner = SOLANA_ADDRESS,
                program = program,
                programId = programId,
                amount = "5000000",
                decimals = 6,
                uiAmountString = "5"
            )
        }

        fun tonChain(): Chain {
            return Chain(
                id = "ton-mainnet",
                paraId = null,
                rank = null,
                name = "TON",
                minSupportedVersion = null,
                assets = listOf(tonAsset("ton-mainnet")),
                nodes = emptyList(),
                explorers = emptyList(),
                externalApi = Chain.ExternalApi(
                    staking = null,
                    history = Chain.ExternalApi.Section(
                        Chain.ExternalApi.Section.Type.TON,
                        UniversalWalletRegistry.TON_INDEXER_BASE_URL
                    ),
                    crowdloans = null
                ),
                icon = "",
                addressPrefix = 0,
                isEthereumBased = false,
                isTestNet = false,
                hasCrowdloans = false,
                parentId = null,
                supportStakingPool = false,
                isEthereumChain = false,
                chainlinkProvider = false,
                supportNft = false,
                isUsesAppId = false,
                identityChain = null,
                ecosystem = Ecosystem.Ton,
                remoteAssetsSource = null
            )
        }

        fun tonAsset(chainId: String): Asset {
            return Asset(
                id = "TON",
                name = "Toncoin",
                symbol = "TON",
                iconUrl = "",
                chainId = chainId,
                chainName = "TON",
                chainIcon = null,
                isTestNet = false,
                priceId = null,
                precision = 9,
                staking = Asset.StakingType.UNSUPPORTED,
                purchaseProviders = null,
                supportStakingPool = false,
                isUtility = true,
                type = ChainAssetType.Normal,
                currencyId = null,
                existentialDeposit = null,
                color = null,
                isNative = true
            )
        }

        fun irohaChain(
            network: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira
        ): Chain {
            val displayName = if (network == UniversalWalletRegistry.nexus) "SORA Nexus" else "Taira Testnet"

            return Chain(
                id = network.id,
                paraId = null,
                rank = null,
                name = displayName,
                minSupportedVersion = null,
                assets = listOf(irohaAsset(network.id)),
                nodes = emptyList(),
                explorers = emptyList(),
                externalApi = Chain.ExternalApi(
                    staking = null,
                    history = Chain.ExternalApi.Section(
                        Chain.ExternalApi.Section.Type.IROHA,
                        network.toriiBaseUrl ?: "https://taira.sora.org"
                    ),
                    crowdloans = null
                ),
                icon = "",
                addressPrefix = 0,
                isEthereumBased = false,
                isTestNet = network == UniversalWalletRegistry.taira,
                hasCrowdloans = false,
                parentId = null,
                supportStakingPool = false,
                isEthereumChain = false,
                chainlinkProvider = false,
                supportNft = false,
                isUsesAppId = false,
                identityChain = null,
                ecosystem = Ecosystem.Substrate,
                remoteAssetsSource = null
            )
        }

        fun walletSmokeMetadata(): LinkedHashMap<String, String> {
            return linkedMapOf(
                "evidence_role" to "wallet-smoke",
                "route_governance_action_hash" to WALLET_SMOKE_ROUTE_HASH,
                "wallet_platform" to "android",
                "wallet_commit" to WALLET_SMOKE_COMMIT
            )
        }

        fun irohaAsset(chainId: String): Asset {
            return Asset(
                id = "xor#sora",
                name = "XOR",
                symbol = "XOR",
                iconUrl = "",
                chainId = chainId,
                chainName = if (chainId == UniversalWalletRegistry.nexus.id) "SORA Nexus" else "Taira Testnet",
                chainIcon = null,
                isTestNet = chainId != UniversalWalletRegistry.nexus.id,
                priceId = null,
                precision = 18,
                staking = Asset.StakingType.UNSUPPORTED,
                purchaseProviders = null,
                supportStakingPool = false,
                isUtility = true,
                type = ChainAssetType.Normal,
                currencyId = null,
                existentialDeposit = null,
                color = null,
                isNative = true
            )
        }
    }
}
