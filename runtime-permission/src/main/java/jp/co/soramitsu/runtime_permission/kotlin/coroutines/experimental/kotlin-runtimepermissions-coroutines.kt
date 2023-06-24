/* ktlint-disable filename */
package jp.co.soramitsu.runtime_permission.kotlin.coroutines.experimental

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import jp.co.soramitsu.runtime_permission.kotlin.NoActivityException
import jp.co.soramitsu.runtime_permission.kotlin.PermissionException
import jp.co.soramitsu.runtime_permission.core.PermissionResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import jp.co.soramitsu.runtime_permission.core.RuntimePermission as RuntimePermission

suspend fun FragmentActivity.askPermission(vararg permissions: String): PermissionResult = suspendCoroutine { continuation ->
    var resumed = false
    RuntimePermission.askPermission(this)
            .request(permissions.toList())
            .onResponse { result ->
                if (!resumed) {
                    resumed = true
                    when {
                        result.isAccepted -> continuation.resume(result)
                        else -> continuation.resumeWithException(PermissionException(result))
                    }
                }
            }
            .ask()
}

suspend fun Fragment.askPermission(vararg permissions: String): PermissionResult = suspendCoroutine { continuation ->
    var resumed = false
    when (activity) {
        null -> continuation.resumeWithException(NoActivityException())
        else -> RuntimePermission.askPermission(this)
                .request(permissions.toList())
                .onResponse { result ->
                    if (!resumed) {
                        resumed = true
                        when {
                            result.isAccepted -> continuation.resume(result)
                            else -> continuation.resumeWithException(PermissionException(result))
                        }
                    }
                }
                .ask()
    }
}
