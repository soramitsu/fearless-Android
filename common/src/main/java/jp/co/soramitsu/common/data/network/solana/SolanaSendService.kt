package jp.co.soramitsu.common.data.network.solana

import jp.co.soramitsu.common.model.UniversalWalletDerivationPaths
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.SolanaTransactionSigner
import java.math.BigInteger

class SolanaSendService(
    private val rpcClient: SolanaRpcClient,
    private val balanceProvider: SolanaSendBalanceProvider? = null
) {
    suspend fun prepare(request: SolanaSendRequest): SolanaPreparedTransferTransaction {
        SolanaTransferTransactionBuilder.requireLamports(request.lamports)
        SolanaTransferTransactionBuilder.normalizeRecipientAddress(request.recipientAddress)

        val account = try {
            SolanaKeyDerivation.deriveAccount(
                mnemonic = request.mnemonic,
                passphrase = request.passphrase,
                derivationPath = request.derivationPath
            )
        } catch (error: IllegalArgumentException) {
            throw SolanaSendServiceException(SolanaSendServiceException.Code.INVALID_ACCOUNT)
        }

        val blockhash = rpcClient.latestBlockhash(
            commitment = request.commitment,
            rpcUrl = request.rpcUrl
        )
        val unsigned = SolanaTransferTransactionBuilder.buildNativeTransfer(
            senderAddress = account.address,
            recipientAddress = request.recipientAddress,
            lamports = request.lamports,
            recentBlockhash = blockhash.value.blockhash
        )
        val fee = rpcClient
            .feeForMessage(
                messageBase64 = unsigned.messageBase64,
                commitment = request.commitment,
                rpcUrl = request.rpcUrl
            )
            .value
            ?: throw SolanaSendServiceException(SolanaSendServiceException.Code.FEE_UNAVAILABLE)
        if (request.validateBalance) {
            requireSufficientSolBalance(
                wallet = account.address,
                requiredLamports = BigInteger.valueOf(request.lamports).add(BigInteger.valueOf(fee))
            )
        }
        val signed = SolanaTransactionSigner.signSerializedTransaction(
            mnemonic = request.mnemonic,
            transactionBase64 = unsigned.transactionBase64,
            expectedSigner = account.address,
            derivationPath = request.derivationPath,
            passphrase = request.passphrase
        )
        val simulation = if (request.simulateBeforeSend) {
            rpcClient.simulateTransaction(
                transactionBase64 = signed.signedTransactionBase64,
                options = request.simulationOptions,
                rpcUrl = request.rpcUrl
            ).also {
                if (it.value.errorJson != null) {
                    throw SolanaSendServiceException(SolanaSendServiceException.Code.SIMULATION_FAILED)
                }
            }
        } else {
            null
        }

        return SolanaPreparedTransferTransaction(
            blockhash = blockhash,
            feeLamports = fee,
            signed = signed,
            simulation = simulation,
            unsigned = unsigned
        )
    }

    suspend fun send(request: SolanaSendRequest): SolanaSentTransferTransaction {
        val prepared = prepare(request)
        val signature = rpcClient.sendRawTransaction(
            transactionBase64 = prepared.signed.signedTransactionBase64,
            options = request.broadcastOptions,
            rpcUrl = request.rpcUrl
        )

        if (signature != prepared.signed.signatureBase58) {
            throw SolanaSendServiceException(SolanaSendServiceException.Code.BROADCAST_SIGNATURE_MISMATCH)
        }

        return SolanaSentTransferTransaction(
            prepared = prepared,
            signature = signature
        )
    }

    suspend fun prepareTokenTransfer(request: SolanaTokenSendRequest): SolanaPreparedTokenTransferTransaction {
        val account = try {
            SolanaKeyDerivation.deriveAccount(
                mnemonic = request.mnemonic,
                passphrase = request.passphrase,
                derivationPath = request.derivationPath
            )
        } catch (error: IllegalArgumentException) {
            throw SolanaSendServiceException(SolanaSendServiceException.Code.INVALID_ACCOUNT)
        }

        validateToken2022TransferPolicy(request)

        val blockhash = rpcClient.latestBlockhash(
            commitment = request.commitment,
            rpcUrl = request.rpcUrl
        )
        val destinationAssociatedTokenAccount = request.destinationWalletAddress?.let {
            val associated = SolanaTransferTransactionBuilder.deriveAssociatedTokenAccountAddress(
                walletAddress = it,
                mintAddress = request.mintAddress,
                tokenProgram = request.tokenProgram
            )
            request.destinationTokenAccount
                ?.takeIf { value -> value.isNotBlank() }
                ?.trim()
                ?.takeUnless { value -> value == associated }
                ?.let {
                    throw SolanaTransferTransactionException(
                        SolanaTransferTransactionException.Code.INVALID_ASSOCIATED_TOKEN_ACCOUNT
                    )
                }
            associated
        }
        val destinationAssociatedTokenAccountExists = destinationAssociatedTokenAccount?.let {
            rpcClient.accountExists(
                address = it,
                commitment = request.commitment,
                rpcUrl = request.rpcUrl
            )
        } ?: false
        val unsigned = SolanaTransferTransactionBuilder.buildTokenSendChecked(
            ownerAddress = account.address,
            sourceTokenAccount = request.sourceTokenAccount,
            destinationTokenAccount = if (destinationAssociatedTokenAccountExists) {
                destinationAssociatedTokenAccount
            } else {
                request.destinationTokenAccount
            },
            mintAddress = request.mintAddress,
            rawAmount = request.rawAmount,
            decimals = request.decimals,
            recentBlockhash = blockhash.value.blockhash,
            tokenProgram = request.tokenProgram,
            extraAccounts = request.extraAccounts,
            destinationWalletAddress = if (destinationAssociatedTokenAccountExists) {
                null
            } else {
                request.destinationWalletAddress
            },
            associatedTokenAccountInstruction = request.associatedTokenAccountInstruction
        )
        val fee = rpcClient
            .feeForMessage(
                messageBase64 = unsigned.messageBase64,
                commitment = request.commitment,
                rpcUrl = request.rpcUrl
            )
            .value
            ?: throw SolanaSendServiceException(SolanaSendServiceException.Code.FEE_UNAVAILABLE)
        if (request.validateBalance) {
            val rentLamports = if (unsigned.createsDestinationAssociatedTokenAccount && balanceProvider != null) {
                rpcClient.minimumBalanceForRentExemption(
                    dataLength = SOLANA_TOKEN_ACCOUNT_DATA_LENGTH,
                    commitment = request.commitment,
                    rpcUrl = request.rpcUrl
                )
            } else {
                0L
            }
            requireSufficientTokenTransferBalance(
                wallet = account.address,
                request = request,
                requiredNativeLamports = BigInteger.valueOf(fee).add(BigInteger.valueOf(rentLamports))
            )
        }
        val signed = SolanaTransactionSigner.signSerializedTransaction(
            mnemonic = request.mnemonic,
            transactionBase64 = unsigned.transactionBase64,
            expectedSigner = account.address,
            derivationPath = request.derivationPath,
            passphrase = request.passphrase
        )
        val simulation = if (request.simulateBeforeSend) {
            rpcClient.simulateTransaction(
                transactionBase64 = signed.signedTransactionBase64,
                options = request.simulationOptions,
                rpcUrl = request.rpcUrl
            ).also {
                if (it.value.errorJson != null) {
                    throw SolanaSendServiceException(SolanaSendServiceException.Code.SIMULATION_FAILED)
                }
            }
        } else {
            null
        }

        return SolanaPreparedTokenTransferTransaction(
            blockhash = blockhash,
            feeLamports = fee,
            signed = signed,
            simulation = simulation,
            unsigned = unsigned
        )
    }

    suspend fun sendTokenTransfer(request: SolanaTokenSendRequest): SolanaSentTokenTransferTransaction {
        val prepared = prepareTokenTransfer(request)
        val signature = rpcClient.sendRawTransaction(
            transactionBase64 = prepared.signed.signedTransactionBase64,
            options = request.broadcastOptions,
            rpcUrl = request.rpcUrl
        )

        if (signature != prepared.signed.signatureBase58) {
            throw SolanaSendServiceException(SolanaSendServiceException.Code.BROADCAST_SIGNATURE_MISMATCH)
        }

        return SolanaSentTokenTransferTransaction(
            prepared = prepared,
            signature = signature
        )
    }

    private suspend fun requireSufficientSolBalance(
        wallet: String,
        requiredLamports: BigInteger
    ) {
        val balances = balanceProvider?.balances(wallet) ?: return
        val available = balances.nativeBalance.amount.toUnsignedBigIntegerOrNull()
            ?: throw SolanaSendServiceException(SolanaSendServiceException.Code.INSUFFICIENT_SOL_BALANCE)

        if (available < requiredLamports) {
            throw SolanaSendServiceException(SolanaSendServiceException.Code.INSUFFICIENT_SOL_BALANCE)
        }
    }

    private suspend fun requireSufficientTokenTransferBalance(
        wallet: String,
        request: SolanaTokenSendRequest,
        requiredNativeLamports: BigInteger
    ) {
        val balances = balanceProvider?.balances(wallet) ?: return
        val availableNative = balances.nativeBalance.amount.toUnsignedBigIntegerOrNull()
            ?: throw SolanaSendServiceException(SolanaSendServiceException.Code.INSUFFICIENT_SOL_BALANCE)
        if (availableNative < requiredNativeLamports) {
            throw SolanaSendServiceException(SolanaSendServiceException.Code.INSUFFICIENT_SOL_BALANCE)
        }

        val tokenBalance = balances.tokenBalances.firstOrNull {
            it.tokenAccountId == request.sourceTokenAccount && it.contractAddress == request.mintAddress
        } ?: throw SolanaSendServiceException(SolanaSendServiceException.Code.INSUFFICIENT_TOKEN_BALANCE)
        if (tokenBalance.decimals != request.decimals) {
            throw SolanaSendServiceException(SolanaSendServiceException.Code.TOKEN_BALANCE_MISMATCH)
        }

        val availableToken = tokenBalance.amount.toUnsignedBigIntegerOrNull()
            ?: throw SolanaSendServiceException(SolanaSendServiceException.Code.INSUFFICIENT_TOKEN_BALANCE)
        if (availableToken < BigInteger.valueOf(request.rawAmount)) {
            throw SolanaSendServiceException(SolanaSendServiceException.Code.INSUFFICIENT_TOKEN_BALANCE)
        }
    }

    private fun validateToken2022TransferPolicy(request: SolanaTokenSendRequest) {
        val metadata = request.tokenMetadata ?: return
        if (!metadata.exists || metadata.mint != request.mintAddress) {
            throw SolanaSendServiceException(SolanaSendServiceException.Code.TOKEN_METADATA_MISMATCH)
        }

        val expectedProgramName = when (request.tokenProgram) {
            SolanaTokenProgram.SplToken -> "spl-token"
            SolanaTokenProgram.Token2022 -> "token-2022"
        }
        if (metadata.program != expectedProgramName || metadata.programId?.let { it != request.tokenProgram.programAddress } == true) {
            throw SolanaSendServiceException(SolanaSendServiceException.Code.TOKEN_METADATA_MISMATCH)
        }
        if (request.tokenProgram != SolanaTokenProgram.Token2022) {
            return
        }

        val extensions = metadata.extensions.orEmpty().toSet()
        if ("nonTransferable" in extensions) {
            throw SolanaSendServiceException(SolanaSendServiceException.Code.UNSUPPORTED_TOKEN_2022_EXTENSION)
        }
        if ((metadata.transferFeeConfig != null || "transferFeeConfig" in extensions) && !request.acknowledgeToken2022TransferFee) {
            throw SolanaSendServiceException(SolanaSendServiceException.Code.TOKEN_2022_TRANSFER_FEE_NOT_ACKNOWLEDGED)
        }
        if ((metadata.transferHook != null || "transferHook" in extensions) && request.extraAccounts.isEmpty()) {
            throw SolanaSendServiceException(SolanaSendServiceException.Code.TOKEN_2022_TRANSFER_HOOK_ACCOUNTS_MISSING)
        }
    }

    private fun String.toUnsignedBigIntegerOrNull(): BigInteger? {
        return takeIf { UNSIGNED_INTEGER.matches(it) }?.let(::BigInteger)
    }

    private companion object {
        const val SOLANA_TOKEN_ACCOUNT_DATA_LENGTH = 165
        val UNSIGNED_INTEGER = Regex("^(0|[1-9][0-9]*)$")
    }
}

data class SolanaSendRequest(
    val mnemonic: String,
    val recipientAddress: String,
    val lamports: Long,
    val passphrase: String = "",
    val derivationPath: String = UniversalWalletDerivationPaths.SOLANA_DEFAULT,
    val commitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed,
    val rpcUrl: String? = null,
    val simulationOptions: SolanaSimulationOptions = SolanaSimulationOptions(
        commitment = commitment,
        replaceRecentBlockhash = false,
        sigVerify = true
    ),
    val broadcastOptions: SolanaBroadcastOptions = SolanaBroadcastOptions(
        preflightCommitment = commitment
    ),
    val simulateBeforeSend: Boolean = true,
    val validateBalance: Boolean = true
)

data class SolanaPreparedTransferTransaction(
    val blockhash: SolanaLatestBlockhashResponse,
    val feeLamports: Long,
    val signed: SolanaTransactionSigner.SignedSolanaTransaction,
    val simulation: SolanaSimulationResponse?,
    val unsigned: SolanaUnsignedTransferTransaction
)

data class SolanaSentTransferTransaction(
    val prepared: SolanaPreparedTransferTransaction,
    val signature: String
)

data class SolanaTokenSendRequest(
    val mnemonic: String,
    val sourceTokenAccount: String,
    val destinationTokenAccount: String? = null,
    val mintAddress: String,
    val rawAmount: Long,
    val decimals: Int,
    val passphrase: String = "",
    val derivationPath: String = UniversalWalletDerivationPaths.SOLANA_DEFAULT,
    val commitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed,
    val rpcUrl: String? = null,
    val tokenProgram: SolanaTokenProgram = SolanaTokenProgram.SplToken,
    val extraAccounts: List<SolanaTokenTransferExtraAccount> = emptyList(),
    val tokenMetadata: SolanaTokenMetadata? = null,
    val acknowledgeToken2022TransferFee: Boolean = false,
    val destinationWalletAddress: String? = null,
    val associatedTokenAccountInstruction: SolanaAssociatedTokenAccountInstruction = SolanaAssociatedTokenAccountInstruction.CreateIdempotent,
    val simulationOptions: SolanaSimulationOptions = SolanaSimulationOptions(
        commitment = commitment,
        replaceRecentBlockhash = false,
        sigVerify = true
    ),
    val broadcastOptions: SolanaBroadcastOptions = SolanaBroadcastOptions(
        preflightCommitment = commitment
    ),
    val simulateBeforeSend: Boolean = true,
    val validateBalance: Boolean = true
)

data class SolanaPreparedTokenTransferTransaction(
    val blockhash: SolanaLatestBlockhashResponse,
    val feeLamports: Long,
    val signed: SolanaTransactionSigner.SignedSolanaTransaction,
    val simulation: SolanaSimulationResponse?,
    val unsigned: SolanaUnsignedTokenSendTransaction
)

data class SolanaSentTokenTransferTransaction(
    val prepared: SolanaPreparedTokenTransferTransaction,
    val signature: String
)

class SolanaSendServiceException(
    val code: Code
) : IllegalArgumentException(code.name) {
    enum class Code {
        INVALID_ACCOUNT,
        FEE_UNAVAILABLE,
        SIMULATION_FAILED,
        BROADCAST_SIGNATURE_MISMATCH,
        INSUFFICIENT_SOL_BALANCE,
        INSUFFICIENT_TOKEN_BALANCE,
        TOKEN_BALANCE_MISMATCH,
        TOKEN_METADATA_MISMATCH,
        TOKEN_2022_TRANSFER_FEE_NOT_ACKNOWLEDGED,
        TOKEN_2022_TRANSFER_HOOK_ACCOUNTS_MISSING,
        UNSUPPORTED_TOKEN_2022_EXTENSION
    }
}

interface SolanaSendBalanceProvider {
    suspend fun balances(wallet: String): SolanaBalanceSyncResult
}

class SolanaBalanceSyncSendBalanceProvider(
    private val balanceSync: SolanaBalanceSync,
    private val network: UniversalWalletRegistry.SolanaNetwork = UniversalWalletRegistry.solanaMainnet,
    private val baseUrl: String? = null
) : SolanaSendBalanceProvider {
    override suspend fun balances(wallet: String): SolanaBalanceSyncResult {
        return balanceSync.balances(
            wallet = wallet,
            network = network,
            baseUrl = baseUrl,
            includeTokenMetadata = false
        )
    }
}
