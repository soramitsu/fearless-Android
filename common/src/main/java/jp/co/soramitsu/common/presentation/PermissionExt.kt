package jp.co.soramitsu.common.presentation

import androidx.fragment.app.Fragment
import jp.co.soramitsu.android_foundation.core.PermissionResult
import jp.co.soramitsu.android_foundation.core.RuntimePermission
import jp.co.soramitsu.android_foundation.kotlin.PermissionException

suspend fun Fragment.askPermissionsSafely(vararg permissions: String): Result<RuntimePermission> =
    try {
        Result.success(RuntimePermission.askPermission(this, *permissions))
    } catch (e: PermissionException) {
        Result.failure(e)
    }

val Result<PermissionResult>.permissionException
    get() = exceptionOrNull() as? PermissionException
