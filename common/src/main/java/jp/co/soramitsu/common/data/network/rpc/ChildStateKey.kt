package jp.co.soramitsu.common.data.network.rpc

import java.io.ByteArrayOutputStream
import jp.co.soramitsu.fearless_utils.extensions.toHexString

private const val CHILD_KEY_DEFAULT = ":child_storage:default:"

suspend fun childStateKey(
    builder: suspend ByteArrayOutputStream.() -> Unit
): String {
    val buffer = ByteArrayOutputStream().apply {
        write(CHILD_KEY_DEFAULT.encodeToByteArray())

        builder()
    }

    return buffer.toByteArray().toHexString(withPrefix = true)
}
