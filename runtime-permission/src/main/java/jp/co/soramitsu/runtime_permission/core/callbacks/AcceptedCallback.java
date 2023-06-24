package jp.co.soramitsu.runtime_permission.core.callbacks;

import jp.co.soramitsu.runtime_permission.core.PermissionResult;

public interface AcceptedCallback {
    void onAccepted(PermissionResult result);
}
