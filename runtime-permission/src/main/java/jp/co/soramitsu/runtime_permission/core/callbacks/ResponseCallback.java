package jp.co.soramitsu.runtime_permission.core.callbacks;

import jp.co.soramitsu.runtime_permission.core.PermissionResult;

public interface ResponseCallback {
    void onResponse(PermissionResult result);
}
