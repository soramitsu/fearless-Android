package jp.co.soramitsu.account.impl.presentation.exporting

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.compose.ui.platform.ComposeView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseFragment

abstract class ExportFragment<V : ExportViewModel> : BaseFragment<V>() {

    companion object {
        const val CHOOSER_REQUEST_CODE = 101
    }

    @CallSuper
    override fun subscribe(viewModel: V) {
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
        childFragmentManager.setFragmentResultListener("security_warning", viewLifecycleOwner) { _, _ ->

        }

        SecurityWarningBottomSheet(
            onConfirm = {},
            onDismiss = viewModel::securityWarningCancel
        ).show(childFragmentManager, "security_warning")
    }
}

class SecurityWarningBottomSheet(
    private val onConfirm: () -> Unit,
    private val onDismiss: () -> Unit
) : BottomSheetDialogFragment() {

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
                        onConfirm()
                        dismiss()
                    },
                    onDismiss = {
                        onDismiss()
                        dismiss()
                    }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBottomSheet()
    }

    private fun setupBottomSheet() {
        dialog?.setOnShowListener {
            val bottomSheetDialog = it as BottomSheetDialog
            val behavior = bottomSheetDialog.behavior
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            behavior.isHideable = false
        }
    }
}
