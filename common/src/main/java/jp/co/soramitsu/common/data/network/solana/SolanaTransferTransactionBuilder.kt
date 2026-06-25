package jp.co.soramitsu.common.data.network.solana

import org.bouncycastle.util.encoders.Base64
import org.bouncycastle.math.ec.rfc8032.Ed25519
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest

object SolanaTransferTransactionBuilder {
    const val SYSTEM_PROGRAM_ADDRESS = "11111111111111111111111111111111"
    const val SPL_TOKEN_PROGRAM_ADDRESS = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    const val TOKEN_2022_PROGRAM_ADDRESS = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
    const val ASSOCIATED_TOKEN_PROGRAM_ADDRESS = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"

    private const val PUBLIC_KEY_BYTES = 32
    private const val SIGNATURE_BYTES = 64
    private const val SYSTEM_TRANSFER_INSTRUCTION = 2L
    private const val TOKEN_TRANSFER_CHECKED_INSTRUCTION = 12
    private const val MAX_EXTRA_TOKEN_ACCOUNTS = 32
    private val PROGRAM_DERIVED_ADDRESS_MARKER = "ProgramDerivedAddress".encodeToByteArray()
    private val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()
    private val BASE58_INDEX = BASE58_ALPHABET.withIndex().associate { it.value to it.index }

    fun buildNativeTransfer(
        senderAddress: String,
        recipientAddress: String,
        lamports: Long,
        recentBlockhash: String
    ): SolanaUnsignedTransferTransaction {
        requireLamports(lamports)

        val normalizedSender = normalizeSenderAddress(senderAddress)
        val normalizedRecipient = normalizeRecipientAddress(recipientAddress)
        val normalizedBlockhash = normalizeRecentBlockhash(recentBlockhash)
        val sender = decodePublicKey(normalizedSender, SolanaTransferTransactionException.Code.INVALID_SENDER)
        val recipient = decodePublicKey(normalizedRecipient, SolanaTransferTransactionException.Code.INVALID_RECIPIENT)
        val systemProgram = decodePublicKey(SYSTEM_PROGRAM_ADDRESS, SolanaTransferTransactionException.Code.INVALID_SENDER)
        val blockhash = decodePublicKey(normalizedBlockhash, SolanaTransferTransactionException.Code.INVALID_RECENT_BLOCKHASH)
        val instructionData = littleEndianU32(SYSTEM_TRANSFER_INSTRUCTION) + littleEndianU64(lamports)

        val message = ByteArrayOutputStream().apply {
            write(byteArrayOf(1, 0, 1))
            write(compactU16(3))
            write(sender)
            write(recipient)
            write(systemProgram)
            write(blockhash)
            write(compactU16(1))
            write(2)
            write(compactU16(2))
            write(byteArrayOf(0, 1))
            write(compactU16(instructionData.size))
            write(instructionData)
        }.toByteArray()

        val transaction = transactionWithSingleEmptySignature(message)

        return SolanaUnsignedTransferTransaction(
            lamports = lamports,
            message = message,
            messageBase64 = Base64.toBase64String(message),
            recentBlockhash = normalizedBlockhash,
            recipientAddress = normalizedRecipient,
            senderAddress = normalizedSender,
            transaction = transaction,
            transactionBase64 = Base64.toBase64String(transaction)
        )
    }

    fun buildTokenTransferChecked(
        ownerAddress: String,
        sourceTokenAccount: String,
        destinationTokenAccount: String,
        mintAddress: String,
        rawAmount: Long,
        decimals: Int,
        recentBlockhash: String,
        tokenProgram: SolanaTokenProgram = SolanaTokenProgram.SplToken,
        extraAccounts: List<SolanaTokenTransferExtraAccount> = emptyList()
    ): SolanaUnsignedTokenTransferTransaction {
        if (rawAmount <= 0) {
            throw SolanaTransferTransactionException(SolanaTransferTransactionException.Code.INVALID_TOKEN_AMOUNT)
        }
        if (decimals !in 0..255) {
            throw SolanaTransferTransactionException(SolanaTransferTransactionException.Code.INVALID_TOKEN_DECIMALS)
        }
        if (extraAccounts.size > MAX_EXTRA_TOKEN_ACCOUNTS) {
            throw SolanaTransferTransactionException(SolanaTransferTransactionException.Code.TOO_MANY_EXTRA_ACCOUNTS)
        }

        val normalizedOwner = normalizePublicKey(ownerAddress, SolanaTransferTransactionException.Code.INVALID_SENDER)
        val normalizedSource = normalizePublicKey(sourceTokenAccount, SolanaTransferTransactionException.Code.INVALID_SOURCE_TOKEN_ACCOUNT)
        val normalizedDestination = normalizePublicKey(destinationTokenAccount, SolanaTransferTransactionException.Code.INVALID_DESTINATION_TOKEN_ACCOUNT)
        val normalizedMint = normalizePublicKey(mintAddress, SolanaTransferTransactionException.Code.INVALID_MINT)
        val normalizedProgram = normalizePublicKey(tokenProgram.programAddress, SolanaTransferTransactionException.Code.INVALID_TOKEN_PROGRAM)
        val normalizedBlockhash = normalizeRecentBlockhash(recentBlockhash)
        val normalizedExtras = extraAccounts.map {
            it.copy(address = normalizePublicKey(it.address, SolanaTransferTransactionException.Code.INVALID_EXTRA_ACCOUNT))
        }
        val allKeys = listOf(
            normalizedOwner,
            normalizedSource,
            normalizedDestination,
            normalizedMint,
            normalizedProgram
        ) + normalizedExtras.map { it.address }
        if (allKeys.distinct().size != allKeys.size) {
            throw SolanaTransferTransactionException(SolanaTransferTransactionException.Code.DUPLICATE_ACCOUNT)
        }

        val writableExtras = normalizedExtras.filter { it.isWritable }
        val readonlyExtras = normalizedExtras.filterNot { it.isWritable }
        val accountKeys = listOf(
            normalizedOwner,
            normalizedSource,
            normalizedDestination
        ) + writableExtras.map { it.address } + listOf(
            normalizedMint,
            normalizedProgram
        ) + readonlyExtras.map { it.address }
        val accountIndex = accountKeys.withIndex().associate { it.value to it.index }
        val instructionAccounts = listOf(
            accountIndex.getValue(normalizedSource),
            accountIndex.getValue(normalizedMint),
            accountIndex.getValue(normalizedDestination),
            accountIndex.getValue(normalizedOwner)
        ) + normalizedExtras.map { accountIndex.getValue(it.address) }
        val instructionData = byteArrayOf(TOKEN_TRANSFER_CHECKED_INSTRUCTION.toByte()) +
            littleEndianU64(rawAmount) +
            byteArrayOf(decimals.toByte())
        val message = ByteArrayOutputStream().apply {
            write(byteArrayOf(1, 0, (2 + readonlyExtras.size).toByte()))
            write(compactU16(accountKeys.size))
            accountKeys.forEach { write(decodePublicKey(it, SolanaTransferTransactionException.Code.INVALID_EXTRA_ACCOUNT)) }
            write(decodePublicKey(normalizedBlockhash, SolanaTransferTransactionException.Code.INVALID_RECENT_BLOCKHASH))
            write(compactU16(1))
            write(accountIndex.getValue(normalizedProgram))
            write(compactU16(instructionAccounts.size))
            write(instructionAccounts.map { it.toByte() }.toByteArray())
            write(compactU16(instructionData.size))
            write(instructionData)
        }.toByteArray()
        val transaction = transactionWithSingleEmptySignature(message)

        return SolanaUnsignedTokenTransferTransaction(
            decimals = decimals,
            destinationTokenAccount = normalizedDestination,
            extraAccounts = normalizedExtras,
            message = message,
            messageBase64 = Base64.toBase64String(message),
            mintAddress = normalizedMint,
            ownerAddress = normalizedOwner,
            rawAmount = rawAmount,
            recentBlockhash = normalizedBlockhash,
            sourceTokenAccount = normalizedSource,
            tokenProgram = tokenProgram,
            transaction = transaction,
            transactionBase64 = Base64.toBase64String(transaction)
        )
    }

    fun buildCreateAssociatedTokenAccount(
        fundingAddress: String,
        walletAddress: String,
        associatedTokenAccount: String? = null,
        mintAddress: String,
        recentBlockhash: String,
        tokenProgram: SolanaTokenProgram = SolanaTokenProgram.SplToken,
        instruction: SolanaAssociatedTokenAccountInstruction = SolanaAssociatedTokenAccountInstruction.CreateIdempotent
    ): SolanaUnsignedAssociatedTokenAccountCreateTransaction {
        val normalizedFunding = normalizePublicKey(fundingAddress, SolanaTransferTransactionException.Code.INVALID_FUNDING_ACCOUNT)
        val normalizedWallet = normalizePublicKey(walletAddress, SolanaTransferTransactionException.Code.INVALID_WALLET)
        val normalizedMint = normalizePublicKey(mintAddress, SolanaTransferTransactionException.Code.INVALID_MINT)
        val normalizedTokenProgram = normalizePublicKey(tokenProgram.programAddress, SolanaTransferTransactionException.Code.INVALID_TOKEN_PROGRAM)
        val normalizedAssociatedProgram = normalizePublicKey(
            ASSOCIATED_TOKEN_PROGRAM_ADDRESS,
            SolanaTransferTransactionException.Code.INVALID_ASSOCIATED_TOKEN_PROGRAM
        )
        val derivedAssociated = deriveAssociatedTokenAccountAddress(
            normalizedWallet,
            normalizedMint,
            normalizedTokenProgram,
            normalizedAssociatedProgram
        )
        val normalizedAssociated = associatedTokenAccount
            ?.takeIf { it.isNotBlank() }
            ?.let { normalizePublicKey(it, SolanaTransferTransactionException.Code.INVALID_ASSOCIATED_TOKEN_ACCOUNT) }
            ?: derivedAssociated
        if (normalizedAssociated != derivedAssociated) {
            throw SolanaTransferTransactionException(SolanaTransferTransactionException.Code.INVALID_ASSOCIATED_TOKEN_ACCOUNT)
        }
        val normalizedBlockhash = normalizeRecentBlockhash(recentBlockhash)
        if (normalizedAssociated == normalizedFunding ||
            normalizedAssociated == normalizedWallet ||
            normalizedAssociated == normalizedMint ||
            normalizedAssociated == SYSTEM_PROGRAM_ADDRESS ||
            normalizedAssociated == normalizedTokenProgram ||
            normalizedAssociated == normalizedAssociatedProgram ||
            normalizedMint == normalizedWallet ||
            normalizedMint == normalizedTokenProgram ||
            normalizedMint == normalizedAssociatedProgram
        ) {
            throw SolanaTransferTransactionException(SolanaTransferTransactionException.Code.DUPLICATE_ACCOUNT)
        }
        val readonlyAccounts = listOf(
            normalizedWallet,
            normalizedMint,
            SYSTEM_PROGRAM_ADDRESS,
            normalizedTokenProgram,
            normalizedAssociatedProgram
        ).distinct().filterNot { it == normalizedFunding || it == normalizedAssociated }
        val accountKeys = listOf(normalizedFunding, normalizedAssociated) + readonlyAccounts
        val accountIndex = accountKeys.withIndex().associate { it.value to it.index }
        val instructionAccounts = listOf(
            accountIndex.getValue(normalizedFunding),
            accountIndex.getValue(normalizedAssociated),
            accountIndex.getValue(normalizedWallet),
            accountIndex.getValue(normalizedMint),
            accountIndex.getValue(SYSTEM_PROGRAM_ADDRESS),
            accountIndex.getValue(normalizedTokenProgram)
        )
        val message = ByteArrayOutputStream().apply {
            write(byteArrayOf(1, 0, readonlyAccounts.size.toByte()))
            write(compactU16(accountKeys.size))
            accountKeys.forEach {
                write(decodePublicKey(it, SolanaTransferTransactionException.Code.INVALID_ASSOCIATED_TOKEN_ACCOUNT))
            }
            write(decodePublicKey(normalizedBlockhash, SolanaTransferTransactionException.Code.INVALID_RECENT_BLOCKHASH))
            write(compactU16(1))
            write(accountIndex.getValue(normalizedAssociatedProgram))
            write(compactU16(instructionAccounts.size))
            write(instructionAccounts.map { it.toByte() }.toByteArray())
            write(compactU16(1))
            write(instruction.discriminator)
        }.toByteArray()
        val transaction = transactionWithSingleEmptySignature(message)

        return SolanaUnsignedAssociatedTokenAccountCreateTransaction(
            associatedTokenAccount = normalizedAssociated,
            fundingAddress = normalizedFunding,
            instruction = instruction,
            message = message,
            messageBase64 = Base64.toBase64String(message),
            mintAddress = normalizedMint,
            recentBlockhash = normalizedBlockhash,
            tokenProgram = tokenProgram,
            transaction = transaction,
            transactionBase64 = Base64.toBase64String(transaction),
            walletAddress = normalizedWallet
        )
    }

    fun buildTokenSendChecked(
        ownerAddress: String,
        sourceTokenAccount: String,
        destinationTokenAccount: String? = null,
        mintAddress: String,
        rawAmount: Long,
        decimals: Int,
        recentBlockhash: String,
        tokenProgram: SolanaTokenProgram = SolanaTokenProgram.SplToken,
        extraAccounts: List<SolanaTokenTransferExtraAccount> = emptyList(),
        destinationWalletAddress: String? = null,
        associatedTokenAccountInstruction: SolanaAssociatedTokenAccountInstruction = SolanaAssociatedTokenAccountInstruction.CreateIdempotent
    ): SolanaUnsignedTokenSendTransaction {
        if (rawAmount <= 0) {
            throw SolanaTransferTransactionException(SolanaTransferTransactionException.Code.INVALID_TOKEN_AMOUNT)
        }
        if (decimals !in 0..255) {
            throw SolanaTransferTransactionException(SolanaTransferTransactionException.Code.INVALID_TOKEN_DECIMALS)
        }
        if (extraAccounts.size > MAX_EXTRA_TOKEN_ACCOUNTS) {
            throw SolanaTransferTransactionException(SolanaTransferTransactionException.Code.TOO_MANY_EXTRA_ACCOUNTS)
        }

        val normalizedOwner = normalizePublicKey(ownerAddress, SolanaTransferTransactionException.Code.INVALID_SENDER)
        val normalizedSource = normalizePublicKey(sourceTokenAccount, SolanaTransferTransactionException.Code.INVALID_SOURCE_TOKEN_ACCOUNT)
        val normalizedDestinationWallet = destinationWalletAddress?.let {
            normalizePublicKey(it, SolanaTransferTransactionException.Code.INVALID_WALLET)
        }
        val normalizedMint = normalizePublicKey(mintAddress, SolanaTransferTransactionException.Code.INVALID_MINT)
        val normalizedProgram = normalizePublicKey(tokenProgram.programAddress, SolanaTransferTransactionException.Code.INVALID_TOKEN_PROGRAM)
        val normalizedAssociatedProgram = normalizePublicKey(
            ASSOCIATED_TOKEN_PROGRAM_ADDRESS,
            SolanaTransferTransactionException.Code.INVALID_ASSOCIATED_TOKEN_PROGRAM
        )
        val derivedAssociated = normalizedDestinationWallet?.let {
            deriveAssociatedTokenAccountAddress(
                walletAddress = it,
                mintAddress = normalizedMint,
                tokenProgramAddress = normalizedProgram,
                associatedTokenProgramAddress = normalizedAssociatedProgram
            )
        }
        val normalizedDestination = destinationTokenAccount
            ?.takeIf { it.isNotBlank() }
            ?.let { normalizePublicKey(it, SolanaTransferTransactionException.Code.INVALID_DESTINATION_TOKEN_ACCOUNT) }
            ?: derivedAssociated
            ?: throw SolanaTransferTransactionException(SolanaTransferTransactionException.Code.INVALID_DESTINATION_TOKEN_ACCOUNT)
        if (derivedAssociated != null && normalizedDestination != derivedAssociated) {
            throw SolanaTransferTransactionException(SolanaTransferTransactionException.Code.INVALID_ASSOCIATED_TOKEN_ACCOUNT)
        }
        val normalizedBlockhash = normalizeRecentBlockhash(recentBlockhash)
        val normalizedExtras = extraAccounts.map {
            it.copy(address = normalizePublicKey(it.address, SolanaTransferTransactionException.Code.INVALID_EXTRA_ACCOUNT))
        }

        val uniqueRoleKeys = mutableListOf(
            normalizedOwner,
            normalizedSource,
            normalizedDestination
        )
        if (normalizedDestinationWallet != null && normalizedDestinationWallet != normalizedOwner) {
            uniqueRoleKeys += normalizedDestinationWallet
        }
        uniqueRoleKeys += normalizedMint
        if (normalizedDestinationWallet != null) {
            uniqueRoleKeys += SYSTEM_PROGRAM_ADDRESS
        }
        uniqueRoleKeys += normalizedProgram
        if (normalizedDestinationWallet != null) {
            uniqueRoleKeys += normalizedAssociatedProgram
        }
        uniqueRoleKeys += normalizedExtras.map { it.address }
        if (uniqueRoleKeys.distinct().size != uniqueRoleKeys.size) {
            throw SolanaTransferTransactionException(SolanaTransferTransactionException.Code.DUPLICATE_ACCOUNT)
        }

        val writableExtras = normalizedExtras.filter { it.isWritable }
        val readonlyExtras = normalizedExtras.filterNot { it.isWritable }
        val unsignedWritableAccounts = listOf(
            normalizedSource,
            normalizedDestination
        ) + writableExtras.map { it.address }
        val unsignedReadonlyAccounts = mutableListOf<String>()
        if (normalizedDestinationWallet != null && normalizedDestinationWallet != normalizedOwner) {
            unsignedReadonlyAccounts += normalizedDestinationWallet
        }
        unsignedReadonlyAccounts += normalizedMint
        if (normalizedDestinationWallet != null) {
            unsignedReadonlyAccounts += SYSTEM_PROGRAM_ADDRESS
        }
        unsignedReadonlyAccounts += normalizedProgram
        if (normalizedDestinationWallet != null) {
            unsignedReadonlyAccounts += normalizedAssociatedProgram
        }
        unsignedReadonlyAccounts += readonlyExtras.map { it.address }

        val accountKeys = listOf(normalizedOwner) + unsignedWritableAccounts + unsignedReadonlyAccounts
        val accountIndex = accountKeys.withIndex().associate { it.value to it.index }
        val instructions = mutableListOf<CompiledSolanaInstruction>()

        if (normalizedDestinationWallet != null) {
            instructions += CompiledSolanaInstruction(
                programAddress = normalizedAssociatedProgram,
                accountAddresses = listOf(
                    normalizedOwner,
                    normalizedDestination,
                    normalizedDestinationWallet,
                    normalizedMint,
                    SYSTEM_PROGRAM_ADDRESS,
                    normalizedProgram
                ),
                data = byteArrayOf(associatedTokenAccountInstruction.discriminator.toByte())
            )
        }

        instructions += CompiledSolanaInstruction(
            programAddress = normalizedProgram,
            accountAddresses = listOf(
                normalizedSource,
                normalizedMint,
                normalizedDestination,
                normalizedOwner
            ) + normalizedExtras.map { it.address },
            data = byteArrayOf(TOKEN_TRANSFER_CHECKED_INSTRUCTION.toByte()) +
                littleEndianU64(rawAmount) +
                byteArrayOf(decimals.toByte())
        )

        val message = ByteArrayOutputStream().apply {
            write(byteArrayOf(1, 0, unsignedReadonlyAccounts.size.toByte()))
            write(compactU16(accountKeys.size))
            accountKeys.forEach {
                write(decodePublicKey(it, SolanaTransferTransactionException.Code.INVALID_EXTRA_ACCOUNT))
            }
            write(decodePublicKey(normalizedBlockhash, SolanaTransferTransactionException.Code.INVALID_RECENT_BLOCKHASH))
            write(compactU16(instructions.size))
            instructions.forEach { instruction ->
                write(accountIndex.getValue(instruction.programAddress))
                write(compactU16(instruction.accountAddresses.size))
                write(instruction.accountAddresses.map { accountIndex.getValue(it).toByte() }.toByteArray())
                write(compactU16(instruction.data.size))
                write(instruction.data)
            }
        }.toByteArray()
        val transaction = transactionWithSingleEmptySignature(message)

        return SolanaUnsignedTokenSendTransaction(
            createsDestinationAssociatedTokenAccount = normalizedDestinationWallet != null,
            decimals = decimals,
            destinationTokenAccount = normalizedDestination,
            destinationWalletAddress = normalizedDestinationWallet,
            extraAccounts = normalizedExtras,
            message = message,
            messageBase64 = Base64.toBase64String(message),
            mintAddress = normalizedMint,
            ownerAddress = normalizedOwner,
            rawAmount = rawAmount,
            recentBlockhash = normalizedBlockhash,
            sourceTokenAccount = normalizedSource,
            tokenProgram = tokenProgram,
            transaction = transaction,
            transactionBase64 = Base64.toBase64String(transaction)
        )
    }

    fun deriveAssociatedTokenAccountAddress(
        walletAddress: String,
        mintAddress: String,
        tokenProgram: SolanaTokenProgram = SolanaTokenProgram.SplToken
    ): String {
        val normalizedWallet = normalizePublicKey(walletAddress, SolanaTransferTransactionException.Code.INVALID_WALLET)
        val normalizedMint = normalizePublicKey(mintAddress, SolanaTransferTransactionException.Code.INVALID_MINT)
        val normalizedTokenProgram = normalizePublicKey(tokenProgram.programAddress, SolanaTransferTransactionException.Code.INVALID_TOKEN_PROGRAM)
        val normalizedAssociatedProgram = normalizePublicKey(
            ASSOCIATED_TOKEN_PROGRAM_ADDRESS,
            SolanaTransferTransactionException.Code.INVALID_ASSOCIATED_TOKEN_PROGRAM
        )

        return deriveAssociatedTokenAccountAddress(
            walletAddress = normalizedWallet,
            mintAddress = normalizedMint,
            tokenProgramAddress = normalizedTokenProgram,
            associatedTokenProgramAddress = normalizedAssociatedProgram
        )
    }

    fun requireLamports(lamports: Long): Long {
        if (lamports <= 0) {
            throw SolanaTransferTransactionException(SolanaTransferTransactionException.Code.INVALID_LAMPORTS)
        }

        return lamports
    }

    fun normalizeSenderAddress(senderAddress: String): String {
        return normalizePublicKey(senderAddress, SolanaTransferTransactionException.Code.INVALID_SENDER)
    }

    fun normalizeRecipientAddress(recipientAddress: String): String {
        return normalizePublicKey(recipientAddress, SolanaTransferTransactionException.Code.INVALID_RECIPIENT)
    }

    fun normalizeRecentBlockhash(recentBlockhash: String): String {
        return normalizePublicKey(recentBlockhash, SolanaTransferTransactionException.Code.INVALID_RECENT_BLOCKHASH)
    }

    private fun normalizePublicKey(value: String, code: SolanaTransferTransactionException.Code): String {
        val normalized = value.trim()
        decodePublicKey(normalized, code)

        return normalized
    }

    private fun decodePublicKey(value: String, code: SolanaTransferTransactionException.Code): ByteArray {
        val decoded = base58Decode(value, code)
        if (decoded.size != PUBLIC_KEY_BYTES) {
            throw SolanaTransferTransactionException(code)
        }

        return decoded
    }

    private fun deriveAssociatedTokenAccountAddress(
        walletAddress: String,
        mintAddress: String,
        tokenProgramAddress: String,
        associatedTokenProgramAddress: String
    ): String {
        val seeds = listOf(
            decodePublicKey(walletAddress, SolanaTransferTransactionException.Code.INVALID_WALLET),
            decodePublicKey(tokenProgramAddress, SolanaTransferTransactionException.Code.INVALID_TOKEN_PROGRAM),
            decodePublicKey(mintAddress, SolanaTransferTransactionException.Code.INVALID_MINT)
        )
        val programId = decodePublicKey(
            associatedTokenProgramAddress,
            SolanaTransferTransactionException.Code.INVALID_ASSOCIATED_TOKEN_PROGRAM
        )

        for (bump in 255 downTo 0) {
            val candidate = createProgramAddress(seeds + byteArrayOf(bump.toByte()), programId) ?: continue
            return base58Encode(candidate)
        }

        throw SolanaTransferTransactionException(SolanaTransferTransactionException.Code.INVALID_ASSOCIATED_TOKEN_ACCOUNT)
    }

    private fun createProgramAddress(seeds: List<ByteArray>, programId: ByteArray): ByteArray? {
        val digest = MessageDigest.getInstance("SHA-256")
        seeds.forEach { digest.update(it) }
        digest.update(programId)
        digest.update(PROGRAM_DERIVED_ADDRESS_MARKER)
        val candidate = digest.digest()

        return candidate.takeUnless { isOnEd25519Curve(it) }
    }

    private fun isOnEd25519Curve(publicKey: ByteArray): Boolean {
        return Ed25519.validatePublicKeyFull(publicKey, 0)
    }

    private fun base58Encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        var number = BigInteger(1, bytes)
        val radix = BigInteger.valueOf(BASE58_ALPHABET.size.toLong())
        val encoded = StringBuilder()

        while (number > BigInteger.ZERO) {
            val divRem = number.divideAndRemainder(radix)
            encoded.append(BASE58_ALPHABET[divRem[1].toInt()])
            number = divRem[0]
        }

        repeat(bytes.takeWhile { it == 0.toByte() }.size) {
            encoded.append(BASE58_ALPHABET[0])
        }

        return encoded.reverse().toString()
    }

    private fun base58Decode(value: String, code: SolanaTransferTransactionException.Code): ByteArray {
        if (value.isEmpty()) {
            throw SolanaTransferTransactionException(code)
        }

        var number = BigInteger.ZERO
        val radix = BigInteger.valueOf(BASE58_ALPHABET.size.toLong())

        value.forEach { character ->
            val digit = BASE58_INDEX[character] ?: throw SolanaTransferTransactionException(code)
            number = number.multiply(radix).add(BigInteger.valueOf(digit.toLong()))
        }

        val body = if (number == BigInteger.ZERO) {
            ByteArray(0)
        } else {
            number.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        }
        val leadingZeroes = value.takeWhile { it == BASE58_ALPHABET[0] }.length

        return ByteArray(leadingZeroes) + body
    }

    private fun compactU16(value: Int): ByteArray {
        require(value in 0..0xffff) { "Compact-u16 value out of range" }

        val result = mutableListOf<Byte>()
        var next = value

        do {
            var byte = next and 0x7f
            next = next shr 7
            if (next > 0) {
                byte = byte or 0x80
            }
            result.add(byte.toByte())
        } while (next > 0)

        return result.toByteArray()
    }

    private fun littleEndianU32(value: Long): ByteArray {
        return ByteArray(4) { index -> ((value ushr (index * 8)) and 0xff).toByte() }
    }

    private fun littleEndianU64(value: Long): ByteArray {
        return ByteArray(8) { index -> ((value ushr (index * 8)) and 0xff).toByte() }
    }

    private fun transactionWithSingleEmptySignature(message: ByteArray): ByteArray {
        return ByteArrayOutputStream().apply {
            write(compactU16(1))
            write(ByteArray(SIGNATURE_BYTES))
            write(message)
        }.toByteArray()
    }

    private data class CompiledSolanaInstruction(
        val programAddress: String,
        val accountAddresses: List<String>,
        val data: ByteArray
    )
}

data class SolanaUnsignedTransferTransaction(
    val lamports: Long,
    val message: ByteArray,
    val messageBase64: String,
    val recentBlockhash: String,
    val recipientAddress: String,
    val senderAddress: String,
    val transaction: ByteArray,
    val transactionBase64: String
)

enum class SolanaTokenProgram(val programAddress: String) {
    SplToken(SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS),
    Token2022(SolanaTransferTransactionBuilder.TOKEN_2022_PROGRAM_ADDRESS)
}

enum class SolanaAssociatedTokenAccountInstruction(val discriminator: Int) {
    Create(0),
    CreateIdempotent(1)
}

data class SolanaTokenTransferExtraAccount(
    val address: String,
    val isWritable: Boolean = false
)

data class SolanaUnsignedTokenTransferTransaction(
    val decimals: Int,
    val destinationTokenAccount: String,
    val extraAccounts: List<SolanaTokenTransferExtraAccount>,
    val message: ByteArray,
    val messageBase64: String,
    val mintAddress: String,
    val ownerAddress: String,
    val rawAmount: Long,
    val recentBlockhash: String,
    val sourceTokenAccount: String,
    val tokenProgram: SolanaTokenProgram,
    val transaction: ByteArray,
    val transactionBase64: String
)

data class SolanaUnsignedAssociatedTokenAccountCreateTransaction(
    val associatedTokenAccount: String,
    val fundingAddress: String,
    val instruction: SolanaAssociatedTokenAccountInstruction,
    val message: ByteArray,
    val messageBase64: String,
    val mintAddress: String,
    val recentBlockhash: String,
    val tokenProgram: SolanaTokenProgram,
    val transaction: ByteArray,
    val transactionBase64: String,
    val walletAddress: String
)

data class SolanaUnsignedTokenSendTransaction(
    val createsDestinationAssociatedTokenAccount: Boolean,
    val decimals: Int,
    val destinationTokenAccount: String,
    val destinationWalletAddress: String?,
    val extraAccounts: List<SolanaTokenTransferExtraAccount>,
    val message: ByteArray,
    val messageBase64: String,
    val mintAddress: String,
    val ownerAddress: String,
    val rawAmount: Long,
    val recentBlockhash: String,
    val sourceTokenAccount: String,
    val tokenProgram: SolanaTokenProgram,
    val transaction: ByteArray,
    val transactionBase64: String
)

class SolanaTransferTransactionException(
    val code: Code
) : IllegalArgumentException(code.name) {
    enum class Code {
        INVALID_SENDER,
        INVALID_RECIPIENT,
        INVALID_RECENT_BLOCKHASH,
        INVALID_LAMPORTS,
        INVALID_SOURCE_TOKEN_ACCOUNT,
        INVALID_DESTINATION_TOKEN_ACCOUNT,
        INVALID_MINT,
        INVALID_TOKEN_PROGRAM,
        INVALID_TOKEN_AMOUNT,
        INVALID_TOKEN_DECIMALS,
        INVALID_EXTRA_ACCOUNT,
        TOO_MANY_EXTRA_ACCOUNTS,
        DUPLICATE_ACCOUNT,
        INVALID_FUNDING_ACCOUNT,
        INVALID_WALLET,
        INVALID_ASSOCIATED_TOKEN_ACCOUNT,
        INVALID_ASSOCIATED_TOKEN_PROGRAM
    }
}
