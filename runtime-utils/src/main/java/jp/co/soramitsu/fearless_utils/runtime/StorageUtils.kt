package jp.co.soramitsu.fearless_utils.runtime

import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b128Concat
import jp.co.soramitsu.fearless_utils.hash.Hasher.xxHash128
import jp.co.soramitsu.fearless_utils.hash.hashConcat

typealias HashFunction = (ByteArray) -> ByteArray

enum class IdentifierHasher(val hasher: HashFunction) {
    Blake2b128concat({ it.blake2b128Concat() }),
    TwoX64Concat(Hasher.xxHash64::hashConcat)
}

class Identifier(
    value: ByteArray,
    identifierHasher: IdentifierHasher
) {
    val id = identifierHasher.hasher(value)
}

object StorageUtils {
    fun createStorageKey(
        service: Service<*>,
        identifier: Identifier?
    ): String {
        val moduleNameBytes = service.module.id.toByteArray()
        val serviceNameBytes = service.id.toByteArray()

        var keyBytes = moduleNameBytes.xxHash128() + serviceNameBytes.xxHash128()

        identifier?.let { keyBytes += it.id }

        return keyBytes.toHexString(withPrefix = true)
    }
}
