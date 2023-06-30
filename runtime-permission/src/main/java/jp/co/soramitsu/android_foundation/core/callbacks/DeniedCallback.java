package jp.co.soramitsu.android_foundation.core.callbacks;

import jp.co.soramitsu.android_foundation.core.PermissionResult;

public interface DeniedCallback {
    void onDenied(PermissionResult result);
}
