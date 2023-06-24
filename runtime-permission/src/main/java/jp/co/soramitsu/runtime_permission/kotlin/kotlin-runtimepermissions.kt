/* ktlint-disable filename */
package jp.co.soramitsu.runtime_permission.kotlin

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import jp.co.soramitsu.runtime_permission.core.RuntimePermission
import jp.co.soramitsu.runtime_permission.core.PermissionResult

fun Fragment.askPermission(vararg permissions: String, acceptedblock: (PermissionResult) -> Unit): KotlinRuntimePermission {
    return KotlinRuntimePermission(
        RuntimePermission.askPermission(activity)
            .request(permissions.toList())
            .onAccepted(acceptedblock)
    )
}

fun FragmentActivity.askPermission(vararg permissions: String, acceptedblock: (PermissionResult) -> Unit): KotlinRuntimePermission {
    return KotlinRuntimePermission(
        RuntimePermission.askPermission(this)
            .request(permissions.toList())
            .onAccepted(acceptedblock)
    )
}

class KotlinRuntimePermission(var runtimePermission: RuntimePermission) {

    init {
        runtimePermission.ask()
    }

    fun onDeclined(block: ((PermissionResult) -> Unit)): KotlinRuntimePermission {
        runtimePermission.onResponse {
            if (it.hasDenied() || it.hasForeverDenied()) {
                block(it)
            }
        }
        return this
    }
}
