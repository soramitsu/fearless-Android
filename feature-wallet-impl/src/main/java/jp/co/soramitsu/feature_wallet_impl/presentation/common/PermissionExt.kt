package jp.co.soramitsu.feature_wallet_impl.presentation.common

import androidx.fragment.app.Fragment
import com.github.florent37.runtimepermission.PermissionResult
import com.github.florent37.runtimepermission.kotlin.PermissionException
import com.github.florent37.runtimepermission.kotlin.coroutines.experimental.askPermission

suspend fun Fragment.askPermissionsSafely(vararg permissions: String): Result<PermissionResult> =
    try {
        Result.success(askPermission(*permissions))
    } catch (e: PermissionException) {
        Result.failure(e)
    }

val Result<PermissionResult>.permissionException
    get() = exceptionOrNull() as? PermissionException