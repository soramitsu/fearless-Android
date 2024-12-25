package jp.co.soramitsu.runtime_permission.core.callbacks;

import jp.co.soramitsu.runtime_permission.core.PermissionResult;

public interface DeniedCallback {
    void onDenied(PermissionResult result);
}
