package jp.co.soramitsu.common.data.storage.encrypt

import android.content.SharedPreferences
import java.io.OutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.DigestOutputStream

/** Retains malformed preference values with their original type; never exposes them as text. */
internal class WalletWrappedKeySnapshot(value: Any?) {
    private val value: Any? = if (value is Set<*>) value.toSet() else value

    fun matches(other: Any?): Boolean = when {
        value is Float && other is Float -> value.toRawBits() == other.toRawBits()
        else -> value == other
    }

    fun retain(editor: SharedPreferences.Editor, key: String) {
        when (value) {
            null -> Unit // A separate boolean records absence without inventing a wrapped value.
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Set<*> -> {
                require(value.all { it is String })
                editor.putStringSet(key, value.map { it as String }.toSet())
            }
            else -> error("Unsupported wrapped-key preference type")
        }
    }

    fun exists(): Boolean = value != null

    fun fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val discard = object : OutputStream() { override fun write(value: Int) = Unit }
        DataOutputStream(DigestOutputStream(discard, digest)).use { output ->
            fun string(value: String) {
                // UTF-16 code units preserve even malformed surrogate strings exactly.
                output.writeInt(value.length)
                value.forEach { output.writeChar(it.code) }
            }
            when (value) {
                null -> output.writeByte(0)
                is String -> { output.writeByte(1); string(value) }
                is Int -> { output.writeByte(2); output.writeInt(value) }
                is Long -> { output.writeByte(3); output.writeLong(value) }
                is Float -> { output.writeByte(4); output.writeInt(value.toRawBits()) }
                is Boolean -> { output.writeByte(5); output.writeBoolean(value) }
                is Set<*> -> {
                    require(value.all { it is String })
                    output.writeByte(6)
                    output.writeInt(value.size)
                    value.map { it as String }.sorted().forEach(::string)
                }
                else -> error("Unsupported wrapped-key preference type")
            }
        }
        return digest.digest()
            .joinToString("") { "%02x".format(it) }
    }

    override fun toString(): String = "WalletWrappedKeySnapshot(redacted)"
}
