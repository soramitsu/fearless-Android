@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.fearless_utils.scale

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.scale.Account.address
import jp.co.soramitsu.fearless_utils.scale.Account.balance
import jp.co.soramitsu.fearless_utils.scale.Account.something
import jp.co.soramitsu.fearless_utils.scale.AccountData.feeFrozen
import jp.co.soramitsu.fearless_utils.scale.AccountData.free
import jp.co.soramitsu.fearless_utils.scale.AccountData.miscFrozen
import jp.co.soramitsu.fearless_utils.scale.AccountData.reserved
import jp.co.soramitsu.fearless_utils.scale.AccountInfo.data
import jp.co.soramitsu.fearless_utils.scale.AccountInfo.nonce
import jp.co.soramitsu.fearless_utils.scale.AccountInfo.refCount
import jp.co.soramitsu.fearless_utils.scale.Address.publicKey
import jp.co.soramitsu.fearless_utils.scale.Balance.value
import jp.co.soramitsu.fearless_utils.scale.DefaultValues.bigInteger
import jp.co.soramitsu.fearless_utils.scale.DefaultValues.bytes
import jp.co.soramitsu.fearless_utils.scale.DefaultValues.text
import jp.co.soramitsu.fearless_utils.scale.Vector.numbers
import jp.co.soramitsu.fearless_utils.scale.dataType.DataType
import jp.co.soramitsu.fearless_utils.scale.dataType.compactInt
import jp.co.soramitsu.fearless_utils.scale.dataType.scalable
import jp.co.soramitsu.fearless_utils.scale.dataType.string
import jp.co.soramitsu.fearless_utils.scale.dataType.uint16
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import jp.co.soramitsu.fearless_utils.scale.dataType.boolean as Bool
import jp.co.soramitsu.fearless_utils.scale.dataType.uint8 as Uint8

object OnlyCompact : Schema<OnlyCompact>() {
    val compact by compactInt()
}

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

    val refCount by uint32()

    val data by schema(AccountData)
}

object Account : Schema<Account>() {
    val address by schema(Address)

    val balance by schema(Balance)

    val something by pair(string, compactInt)
}

object Vector : Schema<Vector>() {
    val numbers by vector(uint16)
}

object EnumTest : Schema<EnumTest>() {
    val intOrBool by enum(
        Uint8, Bool
    )
}

private object EraImmortal : Schema<EraImmortal>()

private object EraMortal : Schema<EraMortal>() {
    val period by uint64()
    val phase by uint64()
}

object EnumTest2 : Schema<EnumTest2>() {
    val era by enum(
        scalable(EraImmortal),
        scalable(EraMortal)
    )
}

object Delimiter : DataType<Byte>() {
    override fun conformsType(value: Any?): Boolean {
        return value is Byte && value == 0
    }

    override fun read(reader: ScaleCodecReader): Byte {
        val read = reader.readByte()

        if (read != 0.toByte()) throw java.lang.IllegalArgumentException("Delimiter is not 0")

        return 0
    }

    override fun write(writer: ScaleCodecWriter, ignored: Byte) {
        writer.writeByte(0)
    }
}

object CustomTypeTest : Schema<CustomTypeTest>() {
    val delimiter by custom(Delimiter, default = 0)
}

private val BYTES_DEFAULT = ByteArray(10) { it.toByte() }
private const val STRING_DEFAULT = "Default"
private val BIG_INT_DEFAULT = BigInteger.TEN

object DefaultValues : Schema<DefaultValues>() {
    val bytes by sizedByteArray(length = 10, default = BYTES_DEFAULT)

    val text by string(default = STRING_DEFAULT)

    val bigInteger by uint128(default = BIG_INT_DEFAULT)
}

@RunWith(MockitoJUnitRunner::class)
class ScaleStructTest {
    @Test
    fun `should read and write complex structure`() {
        val balanceActual = 123.toBigInteger()
        val publicKeyActual = "123"

        val pairActual = "1234" to "12345678901234".toBigInteger()

        val account = Account { account ->
            account[address] = Address { address ->
                address[publicKey] = publicKeyActual
            }

            account[balance] = Balance { balance ->
                balance[value] = balanceActual
            }

            account[something] = pairActual
        }

        val afterIo = writeAndRead(Account, account)

        val publicKey = afterIo[address][publicKey]
        val amount = afterIo[balance][value]
        val something = afterIo[something]

        assertEquals(publicKey, publicKeyActual)
        assertEquals(amount, balanceActual)
        assertEquals(pairActual, something)
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
    fun `should encode compact without sign bit`() {
        val struct = OnlyCompact {
            it[OnlyCompact.compact] = BigDecimal("1.01").scaleByPowerOfTen(12).toBigIntegerExact()
        }

        val expected = "0x0700f4b028eb"
        val actual = struct.toHexString()

        assertEquals(expected, actual)
    }

    @Test
    fun test() {
        val accountInfo = AccountInfo { accountInfo ->
            accountInfo[nonce] = 123.toUInt()

            accountInfo[refCount] = 123.toUInt()

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

    @Test
    fun `should use default values`() {
        val struct = DefaultValues()

        assert(struct[bytes].contentEquals(BYTES_DEFAULT))
        assertEquals(struct[text], STRING_DEFAULT)
        assertEquals(struct[bigInteger], BIG_INT_DEFAULT)
    }

    private fun <S : Schema<S>> writeAndRead(
        schema: S,
        struct: EncodableStruct<S>
    ): EncodableStruct<S> {
        val hex = struct.toHexString()

        println(hex)

        return schema.read(hex)
    }

    @Test
    fun `should encode and decode vector`() {
        val data = listOf(4, 8, 15, 16, 23, 42)

        val struct = Vector {
            it[numbers] = data
        }

        val encoded = struct.toHexString()
        val expected = "0x18040008000f00100017002a00"

        assertEquals(expected, encoded)

        val afterIO = Vector.read(encoded)

        assertEquals(data, afterIO[numbers])
    }

    @Test
    fun `should handle enum`() {
        val enum1 = EnumTest {
            it[EnumTest.intOrBool] = true
        }

        val enum2 = EnumTest {
            it[EnumTest.intOrBool] = 42.toUByte()
        }

        assertEquals(enum1.toHexString(), "0x0101")
        assertEquals(enum2.toHexString(), "0x002a")
    }

    @Test
    fun `should handle enum with structs`() {
        val enum1 = EnumTest2 {
            it[EnumTest2.era] = EraImmortal()
        }

        val enum2 = EnumTest2 {
            it[EnumTest2.era] = EraMortal { era ->
                era[EraMortal.period] = BigInteger.ONE
                era[EraMortal.phase] = BigInteger("3")
            }
        }

        assertEquals(enum1.toHexString(), "0x00")
        assertEquals(enum2.toHexString(), "0x0101000000000000000300000000000000")
    }

    @Test
    fun `should handle custom types`() {
        val struct = CustomTypeTest()

        val expected = "0x00"

        assertEquals(expected, struct.toHexString())

        val afterIo = CustomTypeTest.read(expected)

        assertEquals(struct[CustomTypeTest.delimiter], afterIo[CustomTypeTest.delimiter])
    }
}