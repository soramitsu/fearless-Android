package jp.co.soramitsu.wallet.impl.presentation.balance.list

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.H2
import jp.co.soramitsu.common.compose.component.H4
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.runtime.multiNetwork.ChainState
import jp.co.soramitsu.runtime.multiNetwork.ChainsStateTracker
import kotlinx.coroutines.flow.map

class ServiceFragment : BottomSheetDialogFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.CustomBottomSheetDialogTheme)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setupBottomSheet()
        return ComposeView(requireContext()).apply {
            setContent {
                FearlessTheme {
                    Box(
                        modifier = Modifier
                            .semantics {
                                testTagsAsResourceId = true
                            }
                    ) {
                        val state by ChainsStateTracker.state.map { it.values.toList() }
                            .collectAsState(
                                emptyList()
                            )

                        LazyColumn(content = {
                            items(state) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    H2(text = it.chain.name)
                                    MarginVertical(margin = 8.dp)
                                    Row {
                                        H4(text = "Connection")
                                        Spacer(modifier = Modifier.weight(1f))
                                        H4(
                                            text = connectionText(it.connectionStatus),
                                            color = connectionColor(it.connectionStatus)
                                        )
                                    }
                                    MarginVertical(margin = 8.dp)
                                    Row {
                                        H4(text = "Runtime version")
                                        Spacer(modifier = Modifier.weight(1f))
                                        H4(
                                            text = statusText(it.runtimeVersion),
                                            color = statusColor(it.runtimeVersion)
                                        )
                                    }
                                    MarginVertical(margin = 8.dp)
                                    Row {
                                        H4(text = "Download metadata")
                                        Spacer(modifier = Modifier.weight(1f))
                                        H4(
                                            text = statusText(it.downloadMetadata),
                                            color = statusColor(it.downloadMetadata)
                                        )
                                    }
                                    MarginVertical(margin = 8.dp)
                                    Row {
                                        H4(text = "Runtime construction")
                                        Spacer(modifier = Modifier.weight(1f))
                                        H4(
                                            text = statusText(it.runtimeConstruction),
                                            color = statusColor(it.runtimeConstruction)
                                        )
                                    }
                                }
                            }
                        })
                    }
                }
            }
        }
    }

    private fun connectionColor(status: ChainState.ConnectionStatus?): Color {
        return when (status) {
            is ChainState.ConnectionStatus.Connected -> Color.Green
            is ChainState.ConnectionStatus.Connecting -> Color.Blue
            ChainState.ConnectionStatus.Disconnected -> Color.Black
            ChainState.ConnectionStatus.Failed -> Color.Red
            is ChainState.ConnectionStatus.Paused -> Color.Yellow
            null -> Color.Gray
        }
    }

    private fun connectionText(status: ChainState.ConnectionStatus?): String {
        return when (status) {
            is ChainState.ConnectionStatus.Connected -> "Connected to ${status.node}"
            is ChainState.ConnectionStatus.Connecting -> "Connecting to ${status.node}"
            ChainState.ConnectionStatus.Disconnected -> "Disconnected"
            ChainState.ConnectionStatus.Failed -> "Failed"
            is ChainState.ConnectionStatus.Paused -> "Paused ${status.node}"
            null -> "none"
        }
    }

    private fun statusColor(status: ChainState.Status?): Color {
        return when (status) {
            ChainState.Status.Completed -> Color.Green
            is ChainState.Status.Failed -> Color.Red
            ChainState.Status.Started -> Color.Blue
            null -> Color.Gray
        }
    }

    private fun statusText(status: ChainState.Status?): String {
        return when (status) {
            ChainState.Status.Completed -> "Completed"
            is ChainState.Status.Failed -> "Failed"
            ChainState.Status.Started -> "Started"
            null -> "none"
        }
    }

    private fun setupBottomSheet() {
        dialog?.setOnShowListener {
            val bottomSheetDialog = it as BottomSheetDialog
            setupBehavior(bottomSheetDialog.behavior)
        }
    }

    private fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isDraggable = false
        behavior.isHideable = false
    }
}