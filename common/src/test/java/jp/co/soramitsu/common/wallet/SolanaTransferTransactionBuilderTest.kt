package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.solana.SolanaTransferTransactionBuilder
import jp.co.soramitsu.common.data.network.solana.SolanaTransferTransactionException
import jp.co.soramitsu.common.data.network.solana.SolanaTokenProgram
import jp.co.soramitsu.common.data.network.solana.SolanaTokenTransferExtraAccount
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.SolanaTransactionSigner
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SolanaTransferTransactionBuilderTest {

    @Test
    fun `builds canonical legacy system transfer transaction envelope`() {
        val sender = SolanaKeyDerivation.deriveAccount(MNEMONIC).address
        val unsigned = SolanaTransferTransactionBuilder.buildNativeTransfer(
            senderAddress = " $sender ",
            recipientAddress = " $RECIPIENT ",
            lamports = 123_456_789,
            recentBlockhash = " $BLOCKHASH "
        )
        val parsed = SolanaTransactionSigner.parseSerializedTransaction(unsigned.transaction)

        assertEquals(sender, unsigned.senderAddress)
        assertEquals(RECIPIENT, unsigned.recipientAddress)
        assertEquals(BLOCKHASH, unsigned.recentBlockhash)
        assertEquals(unsigned.messageBase64, parsed.messageBytes.toBase64())
        assertEquals(1, parsed.requiredSignatures)
        assertEquals(0, parsed.readonlySignedAccounts)
        assertEquals(1, parsed.readonlyUnsignedAccounts)
        assertEquals(listOf(sender, RECIPIENT, SolanaTransferTransactionBuilder.SYSTEM_PROGRAM_ADDRESS), parsed.accountKeys)
        assertEquals(BLOCKHASH, parsed.recentBlockhash)
        assertEquals(1, parsed.instructionCount)
        assertEquals(1, parsed.signatureCount)
        assertArrayEquals(ByteArray(64), unsigned.transaction.copyOfRange(1, 65))
        assertTransferInstruction(unsigned.message, 123_456_789)
    }

    @Test
    fun `builds canonical spl token transfer checked transaction envelope`() {
        val unsigned = SolanaTransferTransactionBuilder.buildTokenTransferChecked(
            ownerAddress = OWNER,
            sourceTokenAccount = SOURCE_TOKEN_ACCOUNT,
            destinationTokenAccount = DESTINATION_TOKEN_ACCOUNT,
            mintAddress = MINT,
            rawAmount = 1_234_567,
            decimals = 6,
            recentBlockhash = BLOCKHASH
        )
        val parsed = SolanaTransactionSigner.parseSerializedTransaction(unsigned.transaction)

        assertEquals(OWNER, unsigned.ownerAddress)
        assertEquals(SOURCE_TOKEN_ACCOUNT, unsigned.sourceTokenAccount)
        assertEquals(DESTINATION_TOKEN_ACCOUNT, unsigned.destinationTokenAccount)
        assertEquals(MINT, unsigned.mintAddress)
        assertEquals(SolanaTokenProgram.SplToken, unsigned.tokenProgram)
        assertEquals(1, parsed.requiredSignatures)
        assertEquals(0, parsed.readonlySignedAccounts)
        assertEquals(2, parsed.readonlyUnsignedAccounts)
        assertEquals(
            listOf(
                OWNER,
                SOURCE_TOKEN_ACCOUNT,
                DESTINATION_TOKEN_ACCOUNT,
                MINT,
                SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS
            ),
            parsed.accountKeys
        )
        assertEquals(1, parsed.instructionCount)
        assertArrayEquals(ByteArray(64), unsigned.transaction.copyOfRange(1, 65))
        assertTokenTransferCheckedInstruction(
            message = unsigned.message,
            accountCount = 5,
            programIndex = 4,
            accountIndexes = listOf(1, 3, 2, 0),
            amount = 1_234_567,
            decimals = 6
        )
    }

    @Test
    fun `builds token 2022 transfer checked with extension extra accounts in instruction order`() {
        val unsigned = SolanaTransferTransactionBuilder.buildTokenTransferChecked(
            ownerAddress = OWNER,
            sourceTokenAccount = SOURCE_TOKEN_ACCOUNT,
            destinationTokenAccount = DESTINATION_TOKEN_ACCOUNT,
            mintAddress = MINT,
            rawAmount = Long.MAX_VALUE,
            decimals = 255,
            recentBlockhash = BLOCKHASH,
            tokenProgram = SolanaTokenProgram.Token2022,
            extraAccounts = listOf(
                SolanaTokenTransferExtraAccount(EXTRA_READONLY_ACCOUNT),
                SolanaTokenTransferExtraAccount(EXTRA_WRITABLE_ACCOUNT, isWritable = true)
            )
        )
        val parsed = SolanaTransactionSigner.parseSerializedTransaction(unsigned.transaction)

        assertEquals(SolanaTokenProgram.Token2022, unsigned.tokenProgram)
        assertEquals(0, parsed.readonlySignedAccounts)
        assertEquals(3, parsed.readonlyUnsignedAccounts)
        assertEquals(
            listOf(
                OWNER,
                SOURCE_TOKEN_ACCOUNT,
                DESTINATION_TOKEN_ACCOUNT,
                EXTRA_WRITABLE_ACCOUNT,
                MINT,
                SolanaTransferTransactionBuilder.TOKEN_2022_PROGRAM_ADDRESS,
                EXTRA_READONLY_ACCOUNT
            ),
            parsed.accountKeys
        )
        assertTokenTransferCheckedInstruction(
            message = unsigned.message,
            accountCount = 7,
            programIndex = 5,
            accountIndexes = listOf(1, 4, 2, 0, 6, 3),
            amount = Long.MAX_VALUE,
            decimals = 255
        )
    }

    @Test
    fun `builds idempotent associated token account create with payer wallet de duplicated`() {
        val unsigned = SolanaTransferTransactionBuilder.buildCreateAssociatedTokenAccount(
            fundingAddress = OWNER,
            walletAddress = OWNER,
            mintAddress = MINT,
            recentBlockhash = BLOCKHASH
        )
        val parsed = SolanaTransactionSigner.parseSerializedTransaction(unsigned.transaction)

        assertEquals(OWNER, unsigned.fundingAddress)
        assertEquals(OWNER, unsigned.walletAddress)
        assertEquals(OWNER_SPL_ASSOCIATED_TOKEN_ACCOUNT, unsigned.associatedTokenAccount)
        assertEquals(SolanaTokenProgram.SplToken, unsigned.tokenProgram)
        assertEquals(1, parsed.requiredSignatures)
        assertEquals(0, parsed.readonlySignedAccounts)
        assertEquals(4, parsed.readonlyUnsignedAccounts)
        assertEquals(
            listOf(
                OWNER,
                OWNER_SPL_ASSOCIATED_TOKEN_ACCOUNT,
                MINT,
                SolanaTransferTransactionBuilder.SYSTEM_PROGRAM_ADDRESS,
                SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS,
                SolanaTransferTransactionBuilder.ASSOCIATED_TOKEN_PROGRAM_ADDRESS
            ),
            parsed.accountKeys
        )
        assertAssociatedTokenCreateInstruction(
            message = unsigned.message,
            accountCount = 6,
            programIndex = 5,
            accountIndexes = listOf(0, 1, 0, 2, 3, 4),
            discriminator = 1
        )
    }

    @Test
    fun `builds token 2022 associated token account create with separate payer and create discriminator`() {
        val associatedTokenAccount = SolanaTransferTransactionBuilder.deriveAssociatedTokenAccountAddress(
            walletAddress = OWNER,
            mintAddress = MINT,
            tokenProgram = SolanaTokenProgram.Token2022
        )
        val unsigned = SolanaTransferTransactionBuilder.buildCreateAssociatedTokenAccount(
            fundingAddress = SENDER,
            walletAddress = OWNER,
            associatedTokenAccount = associatedTokenAccount,
            mintAddress = MINT,
            recentBlockhash = BLOCKHASH,
            tokenProgram = SolanaTokenProgram.Token2022,
            instruction = jp.co.soramitsu.common.data.network.solana.SolanaAssociatedTokenAccountInstruction.Create
        )
        val parsed = SolanaTransactionSigner.parseSerializedTransaction(unsigned.transaction)

        assertEquals(SolanaTokenProgram.Token2022, unsigned.tokenProgram)
        assertEquals(5, parsed.readonlyUnsignedAccounts)
        assertEquals(
            listOf(
                SENDER,
                associatedTokenAccount,
                OWNER,
                MINT,
                SolanaTransferTransactionBuilder.SYSTEM_PROGRAM_ADDRESS,
                SolanaTransferTransactionBuilder.TOKEN_2022_PROGRAM_ADDRESS,
                SolanaTransferTransactionBuilder.ASSOCIATED_TOKEN_PROGRAM_ADDRESS
            ),
            parsed.accountKeys
        )
        assertAssociatedTokenCreateInstruction(
            message = unsigned.message,
            accountCount = 7,
            programIndex = 6,
            accountIndexes = listOf(0, 1, 2, 3, 4, 5),
            discriminator = 0
        )
    }

    @Test
    fun `builds token send with idempotent associated token account create then transfer checked`() {
        val unsigned = SolanaTransferTransactionBuilder.buildTokenSendChecked(
            ownerAddress = OWNER,
            sourceTokenAccount = SOURCE_TOKEN_ACCOUNT,
            destinationWalletAddress = DESTINATION_WALLET,
            mintAddress = MINT,
            rawAmount = 99_000,
            decimals = 8,
            recentBlockhash = BLOCKHASH,
            tokenProgram = SolanaTokenProgram.Token2022,
            extraAccounts = listOf(
                SolanaTokenTransferExtraAccount(EXTRA_READONLY_ACCOUNT),
                SolanaTokenTransferExtraAccount(EXTRA_WRITABLE_ACCOUNT, isWritable = true)
            )
        )
        val parsed = SolanaTransactionSigner.parseSerializedTransaction(unsigned.transaction)

        assertEquals(true, unsigned.createsDestinationAssociatedTokenAccount)
        assertEquals(DESTINATION_WALLET, unsigned.destinationWalletAddress)
        assertEquals(DESTINATION_WALLET_TOKEN_2022_ASSOCIATED_TOKEN_ACCOUNT, unsigned.destinationTokenAccount)
        assertEquals(SolanaTokenProgram.Token2022, unsigned.tokenProgram)
        assertEquals(1, parsed.requiredSignatures)
        assertEquals(0, parsed.readonlySignedAccounts)
        assertEquals(6, parsed.readonlyUnsignedAccounts)
        assertEquals(
            listOf(
                OWNER,
                SOURCE_TOKEN_ACCOUNT,
                DESTINATION_WALLET_TOKEN_2022_ASSOCIATED_TOKEN_ACCOUNT,
                EXTRA_WRITABLE_ACCOUNT,
                DESTINATION_WALLET,
                MINT,
                SolanaTransferTransactionBuilder.SYSTEM_PROGRAM_ADDRESS,
                SolanaTransferTransactionBuilder.TOKEN_2022_PROGRAM_ADDRESS,
                SolanaTransferTransactionBuilder.ASSOCIATED_TOKEN_PROGRAM_ADDRESS,
                EXTRA_READONLY_ACCOUNT
            ),
            parsed.accountKeys
        )
        assertEquals(2, parsed.instructionCount)
        assertArrayEquals(ByteArray(64), unsigned.transaction.copyOfRange(1, 65))
        assertTokenSendWithAssociatedAccountCreateInstructions(
            message = unsigned.message,
            accountCount = 10,
            associatedProgramIndex = 8,
            associatedAccountIndexes = listOf(0, 2, 4, 5, 6, 7),
            associatedDiscriminator = 1,
            tokenProgramIndex = 7,
            tokenAccountIndexes = listOf(1, 5, 2, 0, 9, 3),
            amount = 99_000,
            decimals = 8
        )
    }

    @Test
    fun `derives associated token accounts for spl token and token 2022`() {
        assertEquals(
            DESTINATION_WALLET_SPL_ASSOCIATED_TOKEN_ACCOUNT,
            SolanaTransferTransactionBuilder.deriveAssociatedTokenAccountAddress(
                walletAddress = DESTINATION_WALLET,
                mintAddress = MINT
            )
        )
        assertEquals(
            DESTINATION_WALLET_TOKEN_2022_ASSOCIATED_TOKEN_ACCOUNT,
            SolanaTransferTransactionBuilder.deriveAssociatedTokenAccountAddress(
                walletAddress = DESTINATION_WALLET,
                mintAddress = MINT,
                tokenProgram = SolanaTokenProgram.Token2022
            )
        )
    }

    @Test
    fun `rejects malformed transfer parameters`() {
        assertTransferError(SolanaTransferTransactionException.Code.INVALID_LAMPORTS) {
            SolanaTransferTransactionBuilder.buildNativeTransfer(SENDER, RECIPIENT, 0, BLOCKHASH)
        }
        assertTransferError(SolanaTransferTransactionException.Code.INVALID_SENDER) {
            SolanaTransferTransactionBuilder.buildNativeTransfer("not-base58", RECIPIENT, 1, BLOCKHASH)
        }
        assertTransferError(SolanaTransferTransactionException.Code.INVALID_RECIPIENT) {
            SolanaTransferTransactionBuilder.buildNativeTransfer(SENDER, "O0Il", 1, BLOCKHASH)
        }
        assertTransferError(SolanaTransferTransactionException.Code.INVALID_RECENT_BLOCKHASH) {
            SolanaTransferTransactionBuilder.buildNativeTransfer(SENDER, RECIPIENT, 1, "111")
        }
        assertTransferError(SolanaTransferTransactionException.Code.INVALID_TOKEN_AMOUNT) {
            SolanaTransferTransactionBuilder.buildTokenTransferChecked(
                OWNER,
                SOURCE_TOKEN_ACCOUNT,
                DESTINATION_TOKEN_ACCOUNT,
                MINT,
                0,
                6,
                BLOCKHASH
            )
        }
        assertTransferError(SolanaTransferTransactionException.Code.INVALID_TOKEN_DECIMALS) {
            SolanaTransferTransactionBuilder.buildTokenTransferChecked(
                OWNER,
                SOURCE_TOKEN_ACCOUNT,
                DESTINATION_TOKEN_ACCOUNT,
                MINT,
                1,
                256,
                BLOCKHASH
            )
        }
        assertTransferError(SolanaTransferTransactionException.Code.DUPLICATE_ACCOUNT) {
            SolanaTransferTransactionBuilder.buildTokenTransferChecked(
                OWNER,
                SOURCE_TOKEN_ACCOUNT,
                SOURCE_TOKEN_ACCOUNT,
                MINT,
                1,
                6,
                BLOCKHASH
            )
        }
        assertTransferError(SolanaTransferTransactionException.Code.INVALID_WALLET) {
            SolanaTransferTransactionBuilder.buildTokenSendChecked(
                OWNER,
                SOURCE_TOKEN_ACCOUNT,
                DESTINATION_TOKEN_ACCOUNT,
                MINT,
                1,
                6,
                BLOCKHASH,
                destinationWalletAddress = "not-base58"
            )
        }
        assertTransferError(SolanaTransferTransactionException.Code.DUPLICATE_ACCOUNT) {
            SolanaTransferTransactionBuilder.buildTokenSendChecked(
                ownerAddress = OWNER,
                sourceTokenAccount = SOURCE_TOKEN_ACCOUNT,
                mintAddress = MINT,
                rawAmount = 1,
                decimals = 6,
                recentBlockhash = BLOCKHASH,
                destinationWalletAddress = SOURCE_TOKEN_ACCOUNT
            )
        }
        assertTransferError(SolanaTransferTransactionException.Code.DUPLICATE_ACCOUNT) {
            SolanaTransferTransactionBuilder.buildTokenSendChecked(
                ownerAddress = OWNER,
                sourceTokenAccount = SOURCE_TOKEN_ACCOUNT,
                mintAddress = MINT,
                rawAmount = 1,
                decimals = 6,
                recentBlockhash = BLOCKHASH,
                extraAccounts = listOf(SolanaTokenTransferExtraAccount(SolanaTransferTransactionBuilder.ASSOCIATED_TOKEN_PROGRAM_ADDRESS)),
                destinationWalletAddress = DESTINATION_WALLET
            )
        }
        assertTransferError(SolanaTransferTransactionException.Code.INVALID_ASSOCIATED_TOKEN_ACCOUNT) {
            SolanaTransferTransactionBuilder.buildTokenSendChecked(
                OWNER,
                SOURCE_TOKEN_ACCOUNT,
                DESTINATION_TOKEN_ACCOUNT,
                MINT,
                1,
                6,
                BLOCKHASH,
                destinationWalletAddress = DESTINATION_WALLET
            )
        }
        assertTransferError(SolanaTransferTransactionException.Code.INVALID_EXTRA_ACCOUNT) {
            SolanaTransferTransactionBuilder.buildTokenTransferChecked(
                OWNER,
                SOURCE_TOKEN_ACCOUNT,
                DESTINATION_TOKEN_ACCOUNT,
                MINT,
                1,
                6,
                BLOCKHASH,
                extraAccounts = listOf(SolanaTokenTransferExtraAccount("not-base58"))
            )
        }
        assertTransferError(SolanaTransferTransactionException.Code.TOO_MANY_EXTRA_ACCOUNTS) {
            SolanaTransferTransactionBuilder.buildTokenTransferChecked(
                OWNER,
                SOURCE_TOKEN_ACCOUNT,
                DESTINATION_TOKEN_ACCOUNT,
                MINT,
                1,
                6,
                BLOCKHASH,
                extraAccounts = List(33) { SolanaTokenTransferExtraAccount(deriveAddress(it + 10)) }
            )
        }
        assertTransferError(SolanaTransferTransactionException.Code.INVALID_ASSOCIATED_TOKEN_ACCOUNT) {
            SolanaTransferTransactionBuilder.buildCreateAssociatedTokenAccount(
                OWNER,
                OWNER,
                "not-base58",
                MINT,
                BLOCKHASH
            )
        }
        assertTransferError(SolanaTransferTransactionException.Code.DUPLICATE_ACCOUNT) {
            SolanaTransferTransactionBuilder.buildCreateAssociatedTokenAccount(
                fundingAddress = OWNER,
                walletAddress = OWNER,
                mintAddress = OWNER,
                recentBlockhash = BLOCKHASH
            )
        }
        assertTransferError(SolanaTransferTransactionException.Code.INVALID_ASSOCIATED_TOKEN_ACCOUNT) {
            SolanaTransferTransactionBuilder.buildCreateAssociatedTokenAccount(
                OWNER,
                OWNER,
                OWNER,
                MINT,
                BLOCKHASH
            )
        }
    }

    private fun assertTransferInstruction(message: ByteArray, lamports: Long) {
        assertEquals(150, message.size)
        assertArrayEquals(byteArrayOf(1, 0, 1), message.copyOfRange(0, 3))
        assertEquals(3, message[3].toInt())
        assertEquals(1, message[132].toInt())
        assertEquals(2, message[133].toInt())
        assertEquals(2, message[134].toInt())
        assertArrayEquals(byteArrayOf(0, 1), message.copyOfRange(135, 137))
        assertEquals(12, message[137].toInt())
        assertArrayEquals(byteArrayOf(2, 0, 0, 0), message.copyOfRange(138, 142))
        assertEquals(lamports, message.copyOfRange(142, 150).littleEndianLong())
    }

    private fun assertTokenTransferCheckedInstruction(
        message: ByteArray,
        accountCount: Int,
        programIndex: Int,
        accountIndexes: List<Int>,
        amount: Long,
        decimals: Int
    ) {
        var offset = 0
        offset += 3
        assertEquals(accountCount, message[offset].toInt() and 0xff)
        offset += 1 + accountCount * 32
        offset += 32
        assertEquals(1, message[offset].toInt() and 0xff)
        offset += 1
        assertEquals(programIndex, message[offset].toInt() and 0xff)
        offset += 1
        assertEquals(accountIndexes.size, message[offset].toInt() and 0xff)
        offset += 1
        assertArrayEquals(accountIndexes.map { it.toByte() }.toByteArray(), message.copyOfRange(offset, offset + accountIndexes.size))
        offset += accountIndexes.size
        assertEquals(10, message[offset].toInt() and 0xff)
        offset += 1
        assertEquals(12, message[offset].toInt() and 0xff)
        offset += 1
        assertEquals(amount, message.copyOfRange(offset, offset + 8).littleEndianLong())
        offset += 8
        assertEquals(decimals, message[offset].toInt() and 0xff)
        offset += 1
        assertEquals(message.size, offset)
    }

    private fun assertTokenSendWithAssociatedAccountCreateInstructions(
        message: ByteArray,
        accountCount: Int,
        associatedProgramIndex: Int,
        associatedAccountIndexes: List<Int>,
        associatedDiscriminator: Int,
        tokenProgramIndex: Int,
        tokenAccountIndexes: List<Int>,
        amount: Long,
        decimals: Int
    ) {
        var offset = 0
        offset += 3
        assertEquals(accountCount, message[offset].toInt() and 0xff)
        offset += 1 + accountCount * 32
        offset += 32
        assertEquals(2, message[offset].toInt() and 0xff)
        offset += 1

        assertEquals(associatedProgramIndex, message[offset].toInt() and 0xff)
        offset += 1
        assertEquals(associatedAccountIndexes.size, message[offset].toInt() and 0xff)
        offset += 1
        assertArrayEquals(associatedAccountIndexes.map { it.toByte() }.toByteArray(), message.copyOfRange(offset, offset + associatedAccountIndexes.size))
        offset += associatedAccountIndexes.size
        assertEquals(1, message[offset].toInt() and 0xff)
        offset += 1
        assertEquals(associatedDiscriminator, message[offset].toInt() and 0xff)
        offset += 1

        assertEquals(tokenProgramIndex, message[offset].toInt() and 0xff)
        offset += 1
        assertEquals(tokenAccountIndexes.size, message[offset].toInt() and 0xff)
        offset += 1
        assertArrayEquals(tokenAccountIndexes.map { it.toByte() }.toByteArray(), message.copyOfRange(offset, offset + tokenAccountIndexes.size))
        offset += tokenAccountIndexes.size
        assertEquals(10, message[offset].toInt() and 0xff)
        offset += 1
        assertEquals(12, message[offset].toInt() and 0xff)
        offset += 1
        assertEquals(amount, message.copyOfRange(offset, offset + 8).littleEndianLong())
        offset += 8
        assertEquals(decimals, message[offset].toInt() and 0xff)
        offset += 1
        assertEquals(message.size, offset)
    }

    private fun assertAssociatedTokenCreateInstruction(
        message: ByteArray,
        accountCount: Int,
        programIndex: Int,
        accountIndexes: List<Int>,
        discriminator: Int
    ) {
        var offset = 0
        offset += 3
        assertEquals(accountCount, message[offset].toInt() and 0xff)
        offset += 1 + accountCount * 32
        offset += 32
        assertEquals(1, message[offset].toInt() and 0xff)
        offset += 1
        assertEquals(programIndex, message[offset].toInt() and 0xff)
        offset += 1
        assertEquals(accountIndexes.size, message[offset].toInt() and 0xff)
        offset += 1
        assertArrayEquals(accountIndexes.map { it.toByte() }.toByteArray(), message.copyOfRange(offset, offset + accountIndexes.size))
        offset += accountIndexes.size
        assertEquals(1, message[offset].toInt() and 0xff)
        offset += 1
        assertEquals(discriminator, message[offset].toInt() and 0xff)
        offset += 1
        assertEquals(message.size, offset)
    }

    private fun assertTransferError(
        expected: SolanaTransferTransactionException.Code,
        block: () -> Unit
    ) {
        val error = assertThrows(SolanaTransferTransactionException::class.java) { block() }
        assertEquals(expected, error.code)
    }

    private fun ByteArray.littleEndianLong(): Long {
        var value = 0L
        forEachIndexed { index, byte ->
            value = value or ((byte.toLong() and 0xff) shl (index * 8))
        }

        return value
    }

    private fun ByteArray.toBase64(): String = org.bouncycastle.util.encoders.Base64.toBase64String(this)

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val SENDER = SolanaKeyDerivation.deriveAccount(MNEMONIC).address
        val OWNER = deriveAddress(6)
        val SOURCE_TOKEN_ACCOUNT = deriveAddress(1)
        val DESTINATION_TOKEN_ACCOUNT = deriveAddress(2)
        val MINT = deriveAddress(3)
        val EXTRA_READONLY_ACCOUNT = deriveAddress(4)
        val EXTRA_WRITABLE_ACCOUNT = deriveAddress(5)
        val DESTINATION_WALLET = deriveAddress(7)
        const val OWNER_SPL_ASSOCIATED_TOKEN_ACCOUNT = "J6SUEJJ82hffdpF15LHppEoRn3e7rzu4sf4dS1WWzBpA"
        const val DESTINATION_WALLET_SPL_ASSOCIATED_TOKEN_ACCOUNT = "8YLuPLduvEzyNYJRyjRyDvEa3SeQwqsoECRHA4o4kshW"
        const val DESTINATION_WALLET_TOKEN_2022_ASSOCIATED_TOKEN_ACCOUNT = "GJJYkqMbsmdXWHeYUitGJzY7iJ6B9G4wAF6MvrcQgU9m"
        const val RECIPIENT = "So11111111111111111111111111111111111111112"
        const val BLOCKHASH = "7GjNiPun3AzEazTZoFEjZgcBMeuaXdpjHq2raZTmTrfs"

        fun deriveAddress(index: Int): String {
            return SolanaKeyDerivation
                .deriveAccount(MNEMONIC, derivationPath = "m/44'/501'/$index'/0'")
                .address
        }
    }
}
