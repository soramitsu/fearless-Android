package jp.co.soramitsu.common.data.network.solana

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.Base64

/** Composes bounded SOL and standard SPL payments, including priority budget and idempotent ATA creation. */
object SolanaPaymentMessageBuilder {
    const val COMPUTE_BUDGET_PROGRAM = "ComputeBudget111111111111111111111111111111"
    private val MAX_U64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)

    /** Encodes one system transfer; the instruction amount is exact and never adjusted while signing. */
    fun native(sender: String, recipient: String, lamports: Long, blockhash: String, priority: SolanaPriorityFee): SolanaPaymentMessage {
        require(lamports > 0)
        return compose(sender, blockhash, priorityInstructions(priority) + Instruction(
            SolanaTransferTransactionBuilder.SYSTEM_PROGRAM_ADDRESS,
            listOf(Key(sender, writable = true), Key(recipient, writable = true)), byteArrayOf(2, 0, 0, 0) + u64(BigInteger.valueOf(lamports))
        ))
    }

    /** Encodes full-u64 token amounts from up to 16 owned accounts into one exact destination ATA. */
    fun token(sender: String, recipientWallet: String, mint: String, debits: List<SolanaTokenDebit>, decimals: Int,
        createAssociatedAccount: Boolean, blockhash: String, priority: SolanaPriorityFee): SolanaPaymentMessage {
        require(decimals in 0..255 && debits.size in 1..16 && debits.map { it.account }.distinct().size == debits.size)
        require(debits.all { it.amount.signum() > 0 && it.amount <= MAX_U64 } && debits.fold(BigInteger.ZERO) { total, debit -> total + debit.amount } <= MAX_U64)
        val destination = SolanaTransferTransactionBuilder.deriveAssociatedTokenAccountAddress(recipientWallet, mint)
        val instructions = priorityInstructions(priority).toMutableList()
        if (createAssociatedAccount) instructions += Instruction(SolanaTransferTransactionBuilder.ASSOCIATED_TOKEN_PROGRAM_ADDRESS,
            listOf(Key(sender, true), Key(destination, true), Key(recipientWallet), Key(mint),
                Key(SolanaTransferTransactionBuilder.SYSTEM_PROGRAM_ADDRESS), Key(SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS)), byteArrayOf(1))
        debits.forEach { debit ->
            instructions += Instruction(SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS,
                listOf(Key(debit.account, true), Key(mint), Key(destination, true), Key(sender)),
                byteArrayOf(12) + u64(debit.amount) + byteArrayOf(decimals.toByte()))
        }
        return compose(sender, blockhash, instructions)
    }

    private fun priorityInstructions(priority: SolanaPriorityFee) = listOf(
        Instruction(COMPUTE_BUDGET_PROGRAM, emptyList(), byteArrayOf(2) + ByteArray(4) { (priority.computeUnitLimit ushr (8 * it)).toByte() }),
        Instruction(COMPUTE_BUDGET_PROGRAM, emptyList(), byteArrayOf(3) + u64(BigInteger.valueOf(priority.microLamportsPerUnit)))
    )

    private fun compose(sender: String, blockhash: String, instructions: List<Instruction>): SolanaPaymentMessage {
        val keys = linkedMapOf(sender to true)
        instructions.forEach { instruction ->
            keys.putIfAbsent(instruction.program, false)
            instruction.accounts.forEach { key -> keys[key.address] = (keys[key.address] == true) || key.writable }
        }
        require(instructions.all { keys[it.program] == false }) { "Program account cannot be writable" }
        val ordered = listOf(sender) + keys.filter { it.key != sender && it.value }.keys + keys.filter { !it.value }.keys
        require(ordered.size <= 256)
        val message = ByteArrayOutputStream().apply {
            write(byteArrayOf(1, 0, keys.values.count { !it }.toByte()))
            write(compact(ordered.size))
            ordered.forEach { write(SolanaPublicKeyCodec.decode(it)) }
            write(SolanaPublicKeyCodec.decode(blockhash))
            write(compact(instructions.size))
            instructions.forEach { instruction ->
                write(ordered.indexOf(instruction.program))
                write(compact(instruction.accounts.size))
                instruction.accounts.forEach { write(ordered.indexOf(it.address)) }
                write(compact(instruction.data.size))
                write(instruction.data)
            }
        }.toByteArray()
        val transaction = byteArrayOf(1) + ByteArray(64) + message
        require(transaction.size <= 1232) { "Solana transaction exceeds packet limit" }
        return SolanaPaymentMessage(Base64.getEncoder().encodeToString(message), Base64.getEncoder().encodeToString(transaction))
    }

    private fun u64(value: BigInteger): ByteArray {
        require(value.signum() >= 0 && value <= MAX_U64)
        return ByteArray(8) { value.shiftRight(8 * it).toByte() }
    }

    private fun compact(value: Int): ByteArray {
        require(value in 0..65535)
        var remaining = value
        val bytes = ByteArrayOutputStream()
        do {
            val next = remaining and 127
            remaining = remaining ushr 7
            bytes.write(next or if (remaining > 0) 128 else 0)
        } while (remaining > 0)
        return bytes.toByteArray()
    }

    private data class Key(val address: String, val writable: Boolean = false)
    private data class Instruction(val program: String, val accounts: List<Key>, val data: ByteArray)
}
