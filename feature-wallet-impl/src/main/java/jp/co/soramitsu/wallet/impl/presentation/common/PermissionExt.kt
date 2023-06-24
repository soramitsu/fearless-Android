package jp.co.soramitsu.wallet.impl.presentation.common

import androidx.fragment.app.Fragment
import jp.co.soramitsu.runtime_permission.core.PermissionResult
import jp.co.soramitsu.runtime_permission.core.RuntimePermission
import jp.co.soramitsu.runtime_permission.kotlin.PermissionException

suspend fun Fragment.askPermissionsSafely(vararg permissions: String): Result<RuntimePermission> =
    try {
        Result.success(RuntimePermission.askPermission(this, *permissions))
    } catch (e: PermissionException) {
        Result.failure(e)
    }

val Result<PermissionResult>.permissionException
    get() = exceptionOrNull() as? PermissionException
