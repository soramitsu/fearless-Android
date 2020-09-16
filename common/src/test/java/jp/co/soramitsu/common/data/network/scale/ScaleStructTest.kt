@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.common.data.network.scale

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.common.data.network.scale.Account.address
import jp.co.soramitsu.common.data.network.scale.Account.balance
import jp.co.soramitsu.common.data.network.scale.Address.publicKey
import jp.co.soramitsu.common.data.network.scale.Balance.amount
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.io.ByteArrayOutputStream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.lang.IllegalArgumentException


object Address : Schema<Address>() {
    val publicKey by string().optional()
}

object Balance : Schema<Balance>() {
    val amount by uint32()
}

object Account : Schema<Account>() {
    val address by struct(Address)

    val balance by struct(Balance)
}

@RunWith(MockitoJUnitRunner::class)
class ScaleStructTest {
    @Test
    fun `should read and write complex structure`() {
        val balanceActual = 123.toUInt()
        val publicKeyActual = "123"

        val account = Account { account ->
            account[address] = Address { address ->
                address[publicKey] = publicKeyActual
            }

            account[balance] = Balance { balance ->
                balance[amount] = balanceActual
            }
        }

        val afterIo = writeAndRead(Account, account)

        val publicKey = afterIo[address][publicKey]
        val amount = afterIo[balance][amount]

        assertEquals(publicKey, publicKeyActual)
        assertEquals(amount, balanceActual)
    }

    @Test
    fun `should handle optional`() {
        val address = Address()

        assertNull(address[publicKey])

        val afterIo = writeAndRead(Address, address)

        assertNull(afterIo[publicKey], null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `should throw error if nonnull field was not filled`() {
        val balance = Balance()

        balance[amount]
    }

    private fun <S : Schema<S>> writeAndRead(schema: S, struct: EncodableStruct<S>) : EncodableStruct<S> {
        val outputStream = ByteArrayOutputStream()

        val writer = ScaleCodecWriter(outputStream)

        writer.write(schema, struct)

        val bytes = outputStream.toByteArray()
        println(bytes.toHexString())

        val reader = ScaleCodecReader(bytes)

        return reader.read(schema)
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}