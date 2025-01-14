package jp.co.soramitsu.runtime_permission.core;

import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

/**
 * DO NOT USE THIS FRAGMENT DIRECTLY!
 * It's only here because fragments have to be public
 */
public class PermissionFragment extends Fragment {

    public static final String LIST_PERMISSIONS = "LIST_PERMISSIONS";

    private static final int REQUEST_CODE = 23;

    private List<String> permissionsList = new ArrayList<>();

    @Nullable
    private PermissionListener listener;

    public PermissionFragment() {

    }

    public static PermissionFragment newInstance(List<String> permissions) {
        final Bundle args = new Bundle();
        args.putStringArrayList(LIST_PERMISSIONS, new ArrayList<String>(permissions));
        final PermissionFragment fragment = new PermissionFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle arguments = getArguments();
        if (arguments != null) {
            final List<String> permissionsArgs = arguments.getStringArrayList(LIST_PERMISSIONS);
            if (permissionsArgs != null) {
                permissionsList.addAll(permissionsArgs);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (permissionsList.size() > 0) {
            final String[] perms = new String[permissionsList.size()];
            permissionsList.toArray(perms);
            requestPermissions(perms, REQUEST_CODE);
        } else {
            // this shouldn't happen, but just to be sure
            getFragmentManager().beginTransaction()
                    .remove(this)
                    .commitAllowingStateLoss();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE && permissions.length > 0 && this.listener != null) {
            final PermissionListener listener = this.listener;

            final List<String> acceptedPermissions = new ArrayList<>();
            final List<String> askAgainPermissions = new ArrayList<>();
            final List<String> refusedPermissions = new ArrayList<>();

            for (int i = 0; i < permissions.length; i++) {
                final String permissionName = permissions[i];
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    acceptedPermissions.add(permissionName);
                } else {
                    if (shouldShowRequestPermissionRationale(permissionName)) {
                        //listener.onDenied(permissionResult);
                        askAgainPermissions.add(permissionName);
                    } else {
                        refusedPermissions.add(permissionName);
                        //listener.onForeverDenied(permissionResult);
                    }
                }
            }
            listener.onRequestPermissionsResult(acceptedPermissions, refusedPermissions, askAgainPermissions);

            getFragmentManager().beginTransaction()
                    .remove(this)
                    .commitAllowingStateLoss();
        }
    }

    public PermissionFragment setListener(@Nullable PermissionListener listener) {
        this.listener = listener;
        return this;
    }

    interface PermissionListener {
        void onRequestPermissionsResult(List<String> acceptedPermissions, List<String> refusedPermissions, List<String> askAgainPermissions);
    }
}
