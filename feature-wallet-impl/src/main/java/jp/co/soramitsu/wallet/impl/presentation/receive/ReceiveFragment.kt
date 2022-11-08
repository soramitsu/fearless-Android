package jp.co.soramitsu.wallet.impl.presentation.receive

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.receive.model.QrSharingPayload

@AndroidEntryPoint
class ReceiveFragment : BaseComposeBottomSheetDialogFragment<ReceiveViewModel>() {

    companion object {
        const val KEY_ASSET_PAYLOAD = "assetPayload"

        fun getBundle(assetPayload: AssetPayload) = bundleOf(KEY_ASSET_PAYLOAD to assetPayload)
    }

    override val viewModel: ReceiveViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.shareEvent.observeEvent(::startQrSharingIntent)
    }

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()

        ReceiveScreen(
            state = state,
            receiveScreenInterface = viewModel.receiveScreenInterface
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
    }

    private fun startQrSharingIntent(qrSharingPayload: QrSharingPayload) {
        val imageUri = FileProvider.getUriForFile(requireActivity(), "${requireActivity().packageName}.provider", qrSharingPayload.qrFile)

        if (imageUri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, qrSharingPayload.shareMessage)
            }

            startActivity(Intent.createChooser(intent, getString(R.string.wallet_receive_description)))
        }
    }
}
