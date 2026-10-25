package jp.co.soramitsu.coredb.recovery

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** Bounded display values. Numeric variants are typed SQLite values, not original input bytes. */
sealed interface EndpointValue {
    val storage: EndpointStorage

    data object Null : EndpointValue {
        override val storage = EndpointStorage.NULL
    }

    data class Integer(val value: Long) : EndpointValue {
        override val storage = EndpointStorage.INTEGER
        override fun toString(): String = "EndpointValue.Integer"
    }

    data class Real(val value: Double) : EndpointValue {
        override val storage = EndpointStorage.REAL
        override fun toString(): String = "EndpointValue.Real"
    }

    data class Text(val byteLength: Long, val state: EndpointTextState, val value: String?) : EndpointValue {
        override val storage = EndpointStorage.TEXT
        init {
            require(byteLength >= 0)
            require((state == EndpointTextState.AVAILABLE) == (value != null))
            when (state) {
                EndpointTextState.AVAILABLE -> {
                    require(byteLength <= EndpointInventoryReader.MAXIMUM_TEXT_BYTES)
                    require(checkNotNull(value).length <= EndpointInventoryReader.MAXIMUM_TEXT_BYTES)
                    val bytes = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
                    require(bytes.remaining().toLong() == byteLength)
                }
                EndpointTextState.OVERSIZED -> require(byteLength > EndpointInventoryReader.MAXIMUM_TEXT_BYTES)
                EndpointTextState.INVALID_UTF8 -> require(byteLength in 1..EndpointInventoryReader.MAXIMUM_TEXT_BYTES.toLong())
            }
        }
        override fun toString(): String = "EndpointValue.Text(bytes=$byteLength, state=$state)"
    }

    data class Blob(val byteLength: Long) : EndpointValue {
        override val storage = EndpointStorage.BLOB
        init { require(byteLength >= 0) }
        override fun toString(): String = "EndpointValue.Blob(bytes=$byteLength)"
    }
}
