package jp.co.soramitsu.account.impl.presentation.exporting

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.CallSuper
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseFragment

abstract class ExportFragment<V : ExportViewModel> : BaseFragment<V>() {

    private lateinit var securityWarningResultGate: SecurityWarningResultGate

    companion object {
        const val CHOOSER_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        securityWarningResultGate = SecurityWarningResultGate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        securityWarningResultGate.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @CallSuper
    override fun subscribe(viewModel: V) {
        childFragmentManager.bindSecurityWarningResult(
            viewLifecycleOwner,
            securityWarningResultGate,
            viewModel::securityWarningCancel
        )

        viewModel.showSecurityWarningEvent.observeEvent {
            showSecurityWarning()
        }

        viewModel.exportEvent.observeEvent(::shareText)
    }

    private fun shareText(text: String) {
        val title = getString(R.string.common_share)

        val intent = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_TEXT, text)
            .setType("text/plain")

        val chooser = Intent.createChooser(intent, title)

        startActivityForResult(chooser, CHOOSER_REQUEST_CODE)
    }

    private fun showSecurityWarning() {
        if (
            childFragmentManager.showSecurityWarningSheet() ==
            SecurityWarningShowResult.FAILED_CLOSED
        ) {
            deliverSecurityWarningCancelOnce()
        }
    }

    private fun deliverSecurityWarningCancelOnce() {
        if (securityWarningResultGate.consumeCancellation()) {
            viewModel.securityWarningCancel()
        }
    }
}

internal enum class SecurityWarningShowResult {
    SHOWN,
    ALREADY_VISIBLE,
    FAILED_CLOSED
}

internal class SecurityWarningResultGate(savedInstanceState: Bundle?) {

    private var cancellationDelivered =
        savedInstanceState?.getBoolean(CANCELLATION_DELIVERED_STATE_KEY) == true

    fun consumeResult(result: Bundle): Boolean {
        val action = result.getString(
            SecurityWarningBottomSheet.RESULT_ACTION_KEY
        )

        return if (action == SecurityWarningBottomSheet.ACTION_CONFIRM) {
            false
        } else {
            consumeCancellation()
        }
    }

    fun consumeCancellation(): Boolean {
        if (cancellationDelivered) return false

        cancellationDelivered = true
        return true
    }

    fun saveState(outState: Bundle) {
        outState.putBoolean(
            CANCELLATION_DELIVERED_STATE_KEY,
            cancellationDelivered
        )
    }

    private companion object {
        const val CANCELLATION_DELIVERED_STATE_KEY =
            "security_warning_cancellation_delivered"
    }
}

internal fun FragmentManager.bindSecurityWarningResult(
    lifecycleOwner: LifecycleOwner,
    resultGate: SecurityWarningResultGate,
    onCancel: () -> Unit
) {
    setFragmentResultListener(
        SecurityWarningBottomSheet.REQUEST_KEY,
        lifecycleOwner
    ) { _, result ->
        if (resultGate.consumeResult(result)) {
            onCancel()
        }
    }
}

internal fun FragmentManager.showSecurityWarningSheet(): SecurityWarningShowResult {
    val existing = findFragmentByTag(SecurityWarningBottomSheet.TAG)
    if (
        existing is SecurityWarningBottomSheet &&
        existing.isRemoving.not()
    ) {
        return SecurityWarningShowResult.ALREADY_VISIBLE
    }
    if (existing != null || isStateSaved || isDestroyed) {
        return SecurityWarningShowResult.FAILED_CLOSED
    }

    return try {
        SecurityWarningBottomSheet().showNow(
            this,
            SecurityWarningBottomSheet.TAG
        )
        SecurityWarningShowResult.SHOWN
    } catch (_: IllegalStateException) {
        SecurityWarningShowResult.FAILED_CLOSED
    }
}

class SecurityWarningBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "security_warning"
        const val REQUEST_KEY = "security_warning_result"
        const val RESULT_ACTION_KEY = "security_warning_result_action"

        const val ACTION_CONFIRM = "confirm"
        const val ACTION_CANCEL = "cancel"
    }

    private var resultPublished = false
    private var platformBackRegistration: AutoCloseable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.CustomBottomSheetDialogThemeNonTranparentScrim)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                SecurityWarningDialog(
                    onConfirm = {
                        publishResult(ACTION_CONFIRM)
                        dismissAllowingStateLoss()
                    },
                    onDismiss = {
                        publishResult(ACTION_CANCEL)
                        dismissAllowingStateLoss()
                    }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBottomSheet()
    }

    override fun onStart() {
        super.onStart()

        val currentDialog = dialog ?: return
        currentDialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK) {
                false
            } else {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    cancelFromBack()
                }
                true
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            platformBackRegistration = Api33BackRegistration.register(
                currentDialog,
                ::cancelFromBack
            )
        }
    }

    override fun onStop() {
        platformBackRegistration?.close()
        platformBackRegistration = null
        dialog?.setOnKeyListener(null)
        super.onStop()
    }

    override fun onCancel(dialog: DialogInterface) {
        publishResult(ACTION_CANCEL)
        super.onCancel(dialog)
    }

    private fun cancelFromBack() {
        dialog?.cancel()
    }

    private fun setupBottomSheet() {
        dialog?.setOnShowListener {
            val bottomSheetDialog = it as BottomSheetDialog
            val behavior = bottomSheetDialog.behavior
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            // Back on Android 13+ reaches cancel only through the hideable path.
            // Dragging remains disabled, so this does not enable swipe dismissal.
            behavior.isHideable = true
        }
    }

    private fun publishResult(action: String) {
        if (resultPublished || isAdded.not()) return

        resultPublished = true
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            bundleOf(RESULT_ACTION_KEY to action)
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object Api33BackRegistration {

    fun register(dialog: Dialog, onBack: () -> Unit): AutoCloseable {
        val dispatcher = dialog.onBackInvokedDispatcher
        val callback = OnBackInvokedCallback(onBack)
        dispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback
        )

        return AutoCloseable {
            dispatcher.unregisterOnBackInvokedCallback(callback)
        }
    }
}
