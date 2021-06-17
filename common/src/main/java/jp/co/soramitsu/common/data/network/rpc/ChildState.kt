package jp.co.soramitsu.common.data.network.rpc

import jp.co.soramitsu.fearless_utils.extensions.toHexString
import java.io.ByteArrayOutputStream

private const val CHILD_KEY_DEFAULT = ":child_storage:default:"

fun childStateKey(
    builder: ByteArrayOutputStream.() -> Unit
) : String {
    val buffer = ByteArrayOutputStream().apply {
        write(CHILD_KEY_DEFAULT.encodeToByteArray())

        builder()
    }

    return buffer.toByteArray().toHexString(withPrefix = true)
}
