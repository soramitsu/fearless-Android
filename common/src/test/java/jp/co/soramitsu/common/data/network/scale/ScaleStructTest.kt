@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.common.data.network.scale

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.common.data.network.scale.Account.address
import jp.co.soramitsu.common.data.network.scale.Account.balance
import jp.co.soramitsu.common.data.network.scale.AccountData.feeFrozen
import jp.co.soramitsu.common.data.network.scale.AccountData.free
import jp.co.soramitsu.common.data.network.scale.AccountData.miscFrozen
import jp.co.soramitsu.common.data.network.scale.AccountData.reserved
import jp.co.soramitsu.common.data.network.scale.AccountInfo.data
import jp.co.soramitsu.common.data.network.scale.AccountInfo.nonce
import jp.co.soramitsu.common.data.network.scale.AccountInfo.refCount
import jp.co.soramitsu.common.data.network.scale.Address.publicKey
import jp.co.soramitsu.common.data.network.scale.Balance.value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.spongycastle.util.encoders.Hex
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext


object Address : Schema<Address>() {
    val publicKey by string().optional()
}

object Balance : Schema<Balance>() {
    val value by uint128()
}

object AccountData : Schema<AccountData>() {
    val free by uint128()
    val reserved by uint128()
    val miscFrozen by uint128()
    val feeFrozen by uint128()
}

object AccountInfo : Schema<AccountInfo>() {
    val nonce by uint32()

    val refCount by uint8()

    val data by schema(AccountData)
}

object Account : Schema<Account>() {
    val address by schema(Address)

    val balance by schema(Balance)
}

@RunWith(MockitoJUnitRunner::class)
class ScaleStructTest {
    @Test
    fun `should read and write complex structure`() {
        val balanceActual = 123.toBigInteger()
        val publicKeyActual = "123"

        val account = Account { account ->
            account[address] = Address { address ->
                address[publicKey] = publicKeyActual
            }

            account[balance] = Balance { balance ->
                balance[value] = balanceActual
            }
        }

        val afterIo = writeAndRead(Account, account)

        val publicKey = afterIo[address][publicKey]
        val amount = afterIo[balance][value]

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

        balance[value]
    }

    @Test
    fun test() {
        val accountInfo = AccountInfo { accountInfo ->
            accountInfo[nonce] = 123.toUInt()

            accountInfo[refCount] = 123.toUByte()

            accountInfo[data] = AccountData { data ->
                data[free] = BigDecimal("1.23e+12", MathContext.DECIMAL128).toBigInteger()
                data[reserved] = BigInteger("0")
                data[miscFrozen] = BigInteger("0")
                data[feeFrozen] = BigInteger("0")
            }
        }

        val newStruct = writeAndRead(AccountInfo, accountInfo)

        assert(newStruct[nonce] == accountInfo[nonce])
        assert(newStruct[data][free] == accountInfo[data][free])
    }

    private fun <S : Schema<S>> writeAndRead(schema: S, struct: EncodableStruct<S>): EncodableStruct<S> {
        val outputStream = ByteArrayOutputStream()

        val writer = ScaleCodecWriter(outputStream)

        writer.write(schema, struct)

        val bytes = outputStream.toByteArray()
        println(bytes.toHexString())

        val reader = ScaleCodecReader(bytes)

        return reader.read(schema)
    }

    private fun ByteArray.toHexString() = Hex.toHexString(this)

}