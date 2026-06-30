package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.solana.SolanaBroadcastOptions
import jp.co.soramitsu.common.data.network.solana.SolanaFeeForMessageResponse
import jp.co.soramitsu.common.data.network.solana.SolanaLatestBlockhash
import jp.co.soramitsu.common.data.network.solana.SolanaLatestBlockhashResponse
import jp.co.soramitsu.common.data.network.solana.SolanaRpcClient
import jp.co.soramitsu.common.data.network.solana.SolanaRpcCommitment
import jp.co.soramitsu.common.data.network.solana.SolanaRpcContext
import jp.co.soramitsu.common.data.network.solana.SolanaSendRequest
import jp.co.soramitsu.common.data.network.solana.SolanaSendBalanceProvider
import jp.co.soramitsu.common.data.network.solana.SolanaSendService
import jp.co.soramitsu.common.data.network.solana.SolanaSendServiceException
import jp.co.soramitsu.common.data.network.solana.SolanaSimulationOptions
import jp.co.soramitsu.common.data.network.solana.SolanaSimulationResponse
import jp.co.soramitsu.common.data.network.solana.SolanaSimulationValue
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSyncResult
import jp.co.soramitsu.common.data.network.solana.SolanaTokenProgram
import jp.co.soramitsu.common.data.network.solana.SolanaTokenSendRequest
import jp.co.soramitsu.common.data.network.solana.SolanaTokenMetadata
import jp.co.soramitsu.common.data.network.solana.SolanaTokenTransferFeeConfig
import jp.co.soramitsu.common.data.network.solana.SolanaTokenTransferExtraAccount
import jp.co.soramitsu.common.data.network.solana.SolanaTokenTransferHook
import jp.co.soramitsu.common.data.network.solana.SolanaTransferTransactionBuilder
import jp.co.soramitsu.common.data.network.solana.SolanaTransferTransactionException
import jp.co.soramitsu.common.model.UniversalWalletEcosystem
import jp.co.soramitsu.common.model.UniversalWalletIndexedAssetBalance
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.SolanaTransactionSigner
import kotlinx.coroutines.runBlocking
import org.bouncycastle.util.encoders.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

class SolanaSendServiceTest {

    @Test
    fun `prepares signed transfer with fee and simulation without broadcasting`() = runBlocking {
        val rpc = FakeSolanaRpcClient()
        val prepared = SolanaSendService(rpc).prepare(request(rpcUrl = DEVNET_RPC))

        assertEquals(5_000L, prepared.feeLamports)
        assertEquals(SENDER, prepared.unsigned.senderAddress)
        assertEquals(RECIPIENT, prepared.unsigned.recipientAddress)
        assertEquals(BLOCKHASH, prepared.unsigned.recentBlockhash)
        assertEquals(SENDER, prepared.signed.signer)
        assertEquals(prepared.unsigned.messageBase64, rpc.feeMessages.single())
        assertEquals(prepared.signed.signedTransactionBase64, rpc.simulatedTransactions.single())
        assertEquals(42L, prepared.simulation?.value?.unitsConsumed)
        assertNull(rpc.sentTransaction)
        assertEquals(List(3) { DEVNET_RPC }, rpc.urls)
    }

    @Test
    fun `sends signed transfer and requires matching broadcast signature`() = runBlocking {
        val rpc = FakeSolanaRpcClient()
        val sent = SolanaSendService(rpc).send(request(rpcUrl = DEVNET_RPC))

        assertEquals(sent.prepared.signed.signatureBase58, sent.signature)
        assertEquals(sent.prepared.signed.signedTransactionBase64, rpc.sentTransaction)
        assertEquals(SolanaRpcCommitment.Finalized, rpc.broadcastOptions?.preflightCommitment)
        assertEquals(3, rpc.broadcastOptions?.maxRetries)
    }

    @Test
    fun `prepares signed token transfer with fee and simulation without broadcasting`() = runBlocking {
        val rpc = FakeSolanaRpcClient()
        val prepared = SolanaSendService(rpc).prepareTokenTransfer(tokenRequest(rpcUrl = DEVNET_RPC))
        val parsed = SolanaTransactionSigner.parseSerializedTransaction(prepared.unsigned.transaction)

        assertEquals(5_000L, prepared.feeLamports)
        assertEquals(SENDER, prepared.unsigned.ownerAddress)
        assertEquals(SOURCE_TOKEN_ACCOUNT, prepared.unsigned.sourceTokenAccount)
        assertEquals(DESTINATION_TOKEN_ACCOUNT, prepared.unsigned.destinationTokenAccount)
        assertEquals(MINT, prepared.unsigned.mintAddress)
        assertEquals(false, prepared.unsigned.createsDestinationAssociatedTokenAccount)
        assertEquals(1, parsed.instructionCount)
        assertEquals(SENDER, prepared.signed.signer)
        assertEquals(prepared.unsigned.messageBase64, rpc.feeMessages.single())
        assertEquals(prepared.signed.signedTransactionBase64, rpc.simulatedTransactions.single())
        assertEquals(42L, prepared.simulation?.value?.unitsConsumed)
        assertNull(rpc.sentTransaction)
        assertEquals(List(3) { DEVNET_RPC }, rpc.urls)
    }

    @Test
    fun `sends token transfer with associated token account create and requires matching signature`() = runBlocking {
        val rpc = FakeSolanaRpcClient()
        val sent = SolanaSendService(rpc).sendTokenTransfer(
            tokenRequest(
                rpcUrl = DEVNET_RPC,
                destinationTokenAccount = null,
                destinationWalletAddress = DESTINATION_WALLET,
                tokenProgram = SolanaTokenProgram.Token2022,
                extraAccounts = listOf(
                    SolanaTokenTransferExtraAccount(EXTRA_READONLY_ACCOUNT),
                    SolanaTokenTransferExtraAccount(EXTRA_WRITABLE_ACCOUNT, isWritable = true)
                )
            )
        )
        val parsed = SolanaTransactionSigner.parseSerializedTransaction(sent.prepared.unsigned.transaction)

        assertEquals(sent.prepared.signed.signatureBase58, sent.signature)
        assertEquals(true, sent.prepared.unsigned.createsDestinationAssociatedTokenAccount)
        assertEquals(DESTINATION_WALLET, sent.prepared.unsigned.destinationWalletAddress)
        assertEquals(DESTINATION_WALLET_TOKEN_2022_ASSOCIATED_TOKEN_ACCOUNT, sent.prepared.unsigned.destinationTokenAccount)
        assertEquals(SolanaTokenProgram.Token2022, sent.prepared.unsigned.tokenProgram)
        assertEquals(2, parsed.instructionCount)
        assertEquals(listOf(DESTINATION_WALLET_TOKEN_2022_ASSOCIATED_TOKEN_ACCOUNT), rpc.accountExistsRequests)
        assertEquals(sent.prepared.signed.signedTransactionBase64, rpc.sentTransaction)
        assertEquals(SolanaRpcCommitment.Finalized, rpc.broadcastOptions?.preflightCommitment)
        assertEquals(3, rpc.broadcastOptions?.maxRetries)
    }

    @Test
    fun `prepares token transfer without associated token account create when destination account exists`() = runBlocking {
        val rpc = FakeSolanaRpcClient(
            existingAccounts = setOf(DESTINATION_WALLET_TOKEN_2022_ASSOCIATED_TOKEN_ACCOUNT)
        )
        val prepared = SolanaSendService(rpc).prepareTokenTransfer(
            tokenRequest(
                destinationTokenAccount = null,
                destinationWalletAddress = DESTINATION_WALLET,
                tokenProgram = SolanaTokenProgram.Token2022
            )
        )
        val parsed = SolanaTransactionSigner.parseSerializedTransaction(prepared.unsigned.transaction)

        assertEquals(false, prepared.unsigned.createsDestinationAssociatedTokenAccount)
        assertEquals(null, prepared.unsigned.destinationWalletAddress)
        assertEquals(DESTINATION_WALLET_TOKEN_2022_ASSOCIATED_TOKEN_ACCOUNT, prepared.unsigned.destinationTokenAccount)
        assertEquals(1, parsed.instructionCount)
        assertEquals(listOf(DESTINATION_WALLET_TOKEN_2022_ASSOCIATED_TOKEN_ACCOUNT), rpc.accountExistsRequests)
    }

    @Test
    fun `rejects invalid request parameters before rpc calls`() {
        val rpc = FakeSolanaRpcClient()
        assertThrows(SolanaTransferTransactionException::class.java) {
            runBlocking { SolanaSendService(rpc).prepare(request(lamports = 0)) }
        }
        assertThrows(SolanaTransferTransactionException::class.java) {
            runBlocking { SolanaSendService(rpc).prepare(request(recipientAddress = "not-base58")) }
        }
        assertEquals(0, rpc.calls)
    }

    @Test
    fun `rejects invalid token request parameters and account derivation failures`() {
        val rpc = FakeSolanaRpcClient()

        assertSendError(SolanaSendServiceException.Code.INVALID_ACCOUNT) {
            SolanaSendService(rpc).prepareTokenTransfer(tokenRequest(derivationPath = "m/44'/501'/0/0'"))
        }
        assertEquals(0, rpc.calls)

        assertTransferError(SolanaTransferTransactionException.Code.INVALID_TOKEN_AMOUNT) {
            SolanaSendService(rpc).prepareTokenTransfer(tokenRequest(rawAmount = 0))
        }
        assertTransferError(SolanaTransferTransactionException.Code.INVALID_WALLET) {
            SolanaSendService(rpc).prepareTokenTransfer(tokenRequest(destinationWalletAddress = "not-base58"))
        }
        assertTransferError(SolanaTransferTransactionException.Code.INVALID_ASSOCIATED_TOKEN_ACCOUNT) {
            SolanaSendService(rpc).prepareTokenTransfer(
                tokenRequest(
                    destinationTokenAccount = DESTINATION_TOKEN_ACCOUNT,
                    destinationWalletAddress = DESTINATION_WALLET,
                    tokenProgram = SolanaTokenProgram.Token2022
                )
            )
        }
        assertEquals(emptyList<String>(), rpc.accountExistsRequests)
    }

    @Test
    fun `rejects unsafe token 2022 extension metadata before rpc calls`() {
        assertSendError(SolanaSendServiceException.Code.TOKEN_METADATA_MISMATCH) {
            SolanaSendService(FakeSolanaRpcClient()).prepareTokenTransfer(
                tokenRequest(
                    tokenProgram = SolanaTokenProgram.Token2022,
                    tokenMetadata = tokenMetadata(mint = DESTINATION_TOKEN_ACCOUNT)
                )
            )
        }
        assertSendError(SolanaSendServiceException.Code.UNSUPPORTED_TOKEN_2022_EXTENSION) {
            SolanaSendService(FakeSolanaRpcClient()).prepareTokenTransfer(
                tokenRequest(
                    tokenProgram = SolanaTokenProgram.Token2022,
                    tokenMetadata = tokenMetadata(extensions = listOf("nonTransferable"))
                )
            )
        }
        assertSendError(SolanaSendServiceException.Code.TOKEN_2022_TRANSFER_FEE_NOT_ACKNOWLEDGED) {
            SolanaSendService(FakeSolanaRpcClient()).prepareTokenTransfer(
                tokenRequest(
                    tokenProgram = SolanaTokenProgram.Token2022,
                    tokenMetadata = tokenMetadata(
                        extensions = listOf("transferFeeConfig"),
                        transferFeeConfig = SolanaTokenTransferFeeConfig(withheldAmount = "0")
                    )
                )
            )
        }
        val hookRpc = FakeSolanaRpcClient()
        assertSendError(SolanaSendServiceException.Code.TOKEN_2022_TRANSFER_HOOK_ACCOUNTS_MISSING) {
            SolanaSendService(hookRpc).prepareTokenTransfer(
                tokenRequest(
                    tokenProgram = SolanaTokenProgram.Token2022,
                    tokenMetadata = tokenMetadata(
                        extensions = listOf("transferHook"),
                        transferHook = SolanaTokenTransferHook(programId = EXTRA_READONLY_ACCOUNT)
                    )
                )
            )
        }

        assertEquals(0, hookRpc.calls)
    }

    @Test
    fun `allows token 2022 transfer fee after acknowledgement and hook with extra accounts`() = runBlocking {
        val feePrepared = SolanaSendService(FakeSolanaRpcClient()).prepareTokenTransfer(
            tokenRequest(
                tokenProgram = SolanaTokenProgram.Token2022,
                tokenMetadata = tokenMetadata(
                    extensions = listOf("transferFeeConfig"),
                    transferFeeConfig = SolanaTokenTransferFeeConfig(withheldAmount = "0")
                ),
                acknowledgeToken2022TransferFee = true
            )
        )
        val hookPrepared = SolanaSendService(FakeSolanaRpcClient()).prepareTokenTransfer(
            tokenRequest(
                tokenProgram = SolanaTokenProgram.Token2022,
                extraAccounts = listOf(SolanaTokenTransferExtraAccount(EXTRA_READONLY_ACCOUNT)),
                tokenMetadata = tokenMetadata(
                    extensions = listOf("transferHook"),
                    transferHook = SolanaTokenTransferHook(programId = EXTRA_READONLY_ACCOUNT)
                )
            )
        )

        assertEquals(SolanaTokenProgram.Token2022, feePrepared.unsigned.tokenProgram)
        assertEquals(SolanaTokenProgram.Token2022, hookPrepared.unsigned.tokenProgram)
        assertEquals(listOf(SolanaTokenTransferExtraAccount(EXTRA_READONLY_ACCOUNT)), hookPrepared.unsigned.extraAccounts)
    }

    @Test
    fun `rejects native send when sol balance cannot cover amount plus fee`() {
        val rpc = FakeSolanaRpcClient()
        val balances = FakeSolanaSendBalanceProvider(nativeLamports = "1004999")

        assertSendError(SolanaSendServiceException.Code.INSUFFICIENT_SOL_BALANCE) {
            SolanaSendService(rpc, balances).prepare(request())
        }

        assertEquals(listOf(SENDER), balances.wallets)
        assertEquals(emptyList<String>(), rpc.simulatedTransactions)
        assertNull(rpc.sentTransaction)
    }

    @Test
    fun `rejects token send when source token balance is missing or too small`() {
        val rpc = FakeSolanaRpcClient()
        val missingTokenBalances = FakeSolanaSendBalanceProvider(nativeLamports = "9999999999")

        assertSendError(SolanaSendServiceException.Code.INSUFFICIENT_TOKEN_BALANCE) {
            SolanaSendService(rpc, missingTokenBalances).prepareTokenTransfer(tokenRequest())
        }

        val tooSmallTokenBalances = FakeSolanaSendBalanceProvider(
            nativeLamports = "9999999999",
            tokenBalances = listOf(tokenIndexedBalance(amount = "999999"))
        )
        assertSendError(SolanaSendServiceException.Code.INSUFFICIENT_TOKEN_BALANCE) {
            SolanaSendService(FakeSolanaRpcClient(), tooSmallTokenBalances).prepareTokenTransfer(tokenRequest())
        }
    }

    @Test
    fun `includes associated token account rent in token send sol balance check`() {
        val rent = 2_039_280L
        val rpc = FakeSolanaRpcClient(rentLamports = rent)
        val balances = FakeSolanaSendBalanceProvider(
            nativeLamports = (5_000L + rent - 1).toString(),
            tokenBalances = listOf(tokenIndexedBalance(amount = "1000000"))
        )

        assertSendError(SolanaSendServiceException.Code.INSUFFICIENT_SOL_BALANCE) {
            SolanaSendService(rpc, balances).prepareTokenTransfer(
                tokenRequest(destinationTokenAccount = null, destinationWalletAddress = DESTINATION_WALLET)
            )
        }

        assertEquals(listOf(165), rpc.rentDataLengthRequests)
        assertEquals(listOf(DESTINATION_WALLET_ASSOCIATED_TOKEN_ACCOUNT), rpc.accountExistsRequests)
        assertEquals(emptyList<String>(), rpc.simulatedTransactions)
    }

    @Test
    fun `skips associated token account rent when destination account exists`() = runBlocking {
        val rpc = FakeSolanaRpcClient(existingAccounts = setOf(DESTINATION_WALLET_ASSOCIATED_TOKEN_ACCOUNT))
        val balances = FakeSolanaSendBalanceProvider(
            nativeLamports = "5000",
            tokenBalances = listOf(tokenIndexedBalance(amount = "1000000"))
        )
        val prepared = SolanaSendService(rpc, balances).prepareTokenTransfer(
            tokenRequest(destinationTokenAccount = null, destinationWalletAddress = DESTINATION_WALLET)
        )

        assertEquals(false, prepared.unsigned.createsDestinationAssociatedTokenAccount)
        assertEquals(emptyList<Int>(), rpc.rentDataLengthRequests)
        assertEquals(listOf(DESTINATION_WALLET_ASSOCIATED_TOKEN_ACCOUNT), rpc.accountExistsRequests)
    }

    @Test
    fun `rejects unavailable fees simulation failures and broadcast mismatches`() {
        assertSendError(SolanaSendServiceException.Code.FEE_UNAVAILABLE) {
            SolanaSendService(FakeSolanaRpcClient(fee = null)).prepare(request())
        }
        assertSendError(SolanaSendServiceException.Code.SIMULATION_FAILED) {
            SolanaSendService(FakeSolanaRpcClient(simulationError = """{"InstructionError":[0,"Custom"]}""")).prepare(request())
        }
        assertSendError(SolanaSendServiceException.Code.BROADCAST_SIGNATURE_MISMATCH) {
            SolanaSendService(FakeSolanaRpcClient(signatureOverride = "5NfHnqDyzT9qyfxZDq2sSskAMGuFZ3VRqW4EQxghKqrKYdKq6cZNW1J34w7qE6nGx1eDQe5s2eKxB2ZtE1xU9qgN")).send(request())
        }
    }

    private class FakeSolanaRpcClient(
        private val fee: Long? = 5_000,
        private val simulationError: String? = null,
        private val signatureOverride: String? = null,
        private val rentLamports: Long = 2_039_280,
        private val existingAccounts: Set<String> = emptySet()
    ) : SolanaRpcClient {
        val feeMessages = mutableListOf<String>()
        val simulatedTransactions = mutableListOf<String>()
        val rentDataLengthRequests = mutableListOf<Int>()
        val accountExistsRequests = mutableListOf<String>()
        val urls = mutableListOf<String?>()
        var broadcastOptions: SolanaBroadcastOptions? = null
            private set
        var calls = 0
            private set
        var sentTransaction: String? = null
            private set

        override suspend fun latestBlockhash(
            commitment: SolanaRpcCommitment,
            rpcUrl: String?
        ): SolanaLatestBlockhashResponse {
            calls += 1
            urls += rpcUrl
            return SolanaLatestBlockhashResponse(
                context = SolanaRpcContext(slot = 1),
                value = SolanaLatestBlockhash(blockhash = BLOCKHASH, lastValidBlockHeight = 99)
            )
        }

        override suspend fun feeForMessage(
            messageBase64: String,
            commitment: SolanaRpcCommitment,
            rpcUrl: String?
        ): SolanaFeeForMessageResponse {
            calls += 1
            urls += rpcUrl
            feeMessages += messageBase64
            return SolanaFeeForMessageResponse(SolanaRpcContext(slot = 2), fee)
        }

        override suspend fun minimumBalanceForRentExemption(
            dataLength: Int,
            commitment: SolanaRpcCommitment,
            rpcUrl: String?
        ): Long {
            calls += 1
            urls += rpcUrl
            rentDataLengthRequests += dataLength
            return rentLamports
        }

        override suspend fun accountExists(
            address: String,
            commitment: SolanaRpcCommitment,
            rpcUrl: String?
        ): Boolean {
            calls += 1
            urls += rpcUrl
            accountExistsRequests += address
            return address in existingAccounts
        }

        override suspend fun simulateTransaction(
            transactionBase64: String,
            options: SolanaSimulationOptions,
            rpcUrl: String?
        ): SolanaSimulationResponse {
            calls += 1
            urls += rpcUrl
            simulatedTransactions += transactionBase64
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
            calls += 1
            urls += rpcUrl
            broadcastOptions = options
            sentTransaction = transactionBase64
            return signatureOverride ?: firstSignature(transactionBase64)
        }
    }

    private class FakeSolanaSendBalanceProvider(
        private val nativeLamports: String,
        private val tokenBalances: List<UniversalWalletIndexedAssetBalance> = emptyList()
    ) : SolanaSendBalanceProvider {
        val wallets = mutableListOf<String>()

        override suspend fun balances(wallet: String): SolanaBalanceSyncResult {
            wallets += wallet
            val native = nativeIndexedBalance(nativeLamports)
            return SolanaBalanceSyncResult(
                wallet = wallet,
                networkId = "solana-mainnet",
                chainId = "solana:mainnet",
                syncedAtMillis = 1L,
                nativeBalance = native,
                tokenBalances = tokenBalances,
                balances = listOf(native) + tokenBalances
            )
        }
    }

    private fun request(
        mnemonic: String = MNEMONIC,
        recipientAddress: String = RECIPIENT,
        lamports: Long = 1_000_000,
        rpcUrl: String? = null
    ): SolanaSendRequest {
        return SolanaSendRequest(
            mnemonic = mnemonic,
            recipientAddress = recipientAddress,
            lamports = lamports,
            commitment = SolanaRpcCommitment.Finalized,
            rpcUrl = rpcUrl,
            broadcastOptions = SolanaBroadcastOptions(maxRetries = 3, preflightCommitment = SolanaRpcCommitment.Finalized)
        )
    }

    private fun tokenRequest(
        mnemonic: String = MNEMONIC,
        sourceTokenAccount: String = SOURCE_TOKEN_ACCOUNT,
        destinationTokenAccount: String? = DESTINATION_TOKEN_ACCOUNT,
        destinationWalletAddress: String? = null,
        mintAddress: String = MINT,
        rawAmount: Long = 1_000_000,
        decimals: Int = 6,
        derivationPath: String = "m/44'/501'/0'/0'",
        rpcUrl: String? = null,
        tokenProgram: SolanaTokenProgram = SolanaTokenProgram.SplToken,
        extraAccounts: List<SolanaTokenTransferExtraAccount> = emptyList(),
        tokenMetadata: SolanaTokenMetadata? = null,
        acknowledgeToken2022TransferFee: Boolean = false
    ): SolanaTokenSendRequest {
        return SolanaTokenSendRequest(
            mnemonic = mnemonic,
            sourceTokenAccount = sourceTokenAccount,
            destinationTokenAccount = destinationTokenAccount,
            destinationWalletAddress = destinationWalletAddress,
            mintAddress = mintAddress,
            rawAmount = rawAmount,
            decimals = decimals,
            derivationPath = derivationPath,
            commitment = SolanaRpcCommitment.Finalized,
            rpcUrl = rpcUrl,
            tokenProgram = tokenProgram,
            extraAccounts = extraAccounts,
            tokenMetadata = tokenMetadata,
            acknowledgeToken2022TransferFee = acknowledgeToken2022TransferFee,
            broadcastOptions = SolanaBroadcastOptions(maxRetries = 3, preflightCommitment = SolanaRpcCommitment.Finalized)
        )
    }

    private fun assertSendError(
        expected: SolanaSendServiceException.Code,
        block: suspend () -> Unit
    ) {
        val error = assertThrows(SolanaSendServiceException::class.java) {
            runBlocking { block() }
        }
        assertEquals(expected, error.code)
    }

    private fun assertTransferError(
        expected: SolanaTransferTransactionException.Code,
        block: suspend () -> Unit
    ) {
        val error = assertThrows(SolanaTransferTransactionException::class.java) {
            runBlocking { block() }
        }
        assertEquals(expected, error.code)
    }

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val SENDER = SolanaKeyDerivation.deriveAccount(MNEMONIC).address
        val SOURCE_TOKEN_ACCOUNT = deriveAddress(1)
        val DESTINATION_TOKEN_ACCOUNT = deriveAddress(2)
        val MINT = deriveAddress(3)
        val EXTRA_READONLY_ACCOUNT = deriveAddress(4)
        val EXTRA_WRITABLE_ACCOUNT = deriveAddress(5)
        val DESTINATION_WALLET = deriveAddress(7)
        const val DESTINATION_WALLET_ASSOCIATED_TOKEN_ACCOUNT = "8YLuPLduvEzyNYJRyjRyDvEa3SeQwqsoECRHA4o4kshW"
        const val DESTINATION_WALLET_TOKEN_2022_ASSOCIATED_TOKEN_ACCOUNT = "GJJYkqMbsmdXWHeYUitGJzY7iJ6B9G4wAF6MvrcQgU9m"
        const val RECIPIENT = "So11111111111111111111111111111111111111112"
        const val BLOCKHASH = "7GjNiPun3AzEazTZoFEjZgcBMeuaXdpjHq2raZTmTrfs"
        const val DEVNET_RPC = "https://api.devnet.solana.com"
        private val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()

        fun deriveAddress(index: Int): String {
            return SolanaKeyDerivation
                .deriveAccount(MNEMONIC, derivationPath = "m/44'/501'/$index'/0'")
                .address
        }

        fun nativeIndexedBalance(amount: String): UniversalWalletIndexedAssetBalance {
            return UniversalWalletIndexedAssetBalance(
                accountId = "solana-mainnet",
                ecosystem = UniversalWalletEcosystem.Solana,
                chainId = "solana:mainnet",
                assetId = "SOL",
                amount = amount,
                decimals = 9,
                isNative = true,
                symbol = "SOL",
                syncedAtMillis = 1L
            )
        }

        fun tokenIndexedBalance(
            amount: String,
            decimals: Int = 6,
            tokenAccount: String = SOURCE_TOKEN_ACCOUNT,
            mint: String = MINT
        ): UniversalWalletIndexedAssetBalance {
            return UniversalWalletIndexedAssetBalance(
                accountId = "solana-mainnet",
                ecosystem = UniversalWalletEcosystem.Solana,
                chainId = "solana:mainnet",
                assetId = mint,
                amount = amount,
                decimals = decimals,
                isNative = false,
                tokenAccountId = tokenAccount,
                contractAddress = mint,
                syncedAtMillis = 1L
            )
        }

        fun tokenMetadata(
            mint: String = MINT,
            exists: Boolean = true,
            program: String = "token-2022",
            programId: String? = SolanaTransferTransactionBuilder.TOKEN_2022_PROGRAM_ADDRESS,
            extensions: List<String> = emptyList(),
            transferFeeConfig: SolanaTokenTransferFeeConfig? = null,
            transferHook: SolanaTokenTransferHook? = null
        ): SolanaTokenMetadata {
            return SolanaTokenMetadata(
                mint = mint,
                exists = exists,
                program = program,
                programId = programId,
                extensions = extensions,
                transferFeeConfig = transferFeeConfig,
                transferHook = transferHook,
                decimals = 6,
                supply = "100000000",
                uiSupplyString = "100",
                mintAuthority = null,
                freezeAuthority = null,
                isInitialized = true,
                name = null,
                symbol = null,
                uri = null,
                syncedAt = 1L
            )
        }

        fun firstSignature(transactionBase64: String): String {
            val transaction = Base64.decode(transactionBase64)
            val parsed = SolanaTransactionSigner.parseSerializedTransaction(transaction)
            return base58Encode(transaction.copyOfRange(parsed.signaturesOffset, parsed.signaturesOffset + 64))
        }

        fun base58Encode(bytes: ByteArray): String {
            var value = BigInteger(1, bytes)
            val output = StringBuilder()
            val radix = BigInteger.valueOf(BASE58_ALPHABET.size.toLong())

            while (value > BigInteger.ZERO) {
                val divRem = value.divideAndRemainder(radix)
                output.append(BASE58_ALPHABET[divRem[1].toInt()])
                value = divRem[0]
            }

            bytes.takeWhile { it == 0.toByte() }.forEach { _ ->
                output.append(BASE58_ALPHABET[0])
            }

            return output.reverse().toString()
        }
    }
}
