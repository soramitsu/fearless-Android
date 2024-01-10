package jp.co.soramitsu.nft.impl.presentation.filters

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Switch
import androidx.compose.material.SwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.H4
import jp.co.soramitsu.common.compose.component.H4Bold
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black3
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.nft.domain.models.NFTFilter
import jp.co.soramitsu.nft.impl.presentation.NftRouter
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class NftFiltersFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var navigator: NftRouter

    companion object {
        private const val KEY_PAYLOAD = "nft_filters_payload"
        private const val KEY_RESULT_DESTINATION_ID = "result_destination_id"
        const val KEY_RESULT = "nft_filters_result"

        fun getBundle(payload: NftFilterModel, resultDestinationId: Int): Bundle {
            return bundleOf(KEY_PAYLOAD to payload.bundle, KEY_RESULT_DESTINATION_ID to resultDestinationId)
        }
    }

    private var state = MutableStateFlow<NftFilterModel?>(null)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val filtersBundle = requireNotNull(requireArguments().getBundle(KEY_PAYLOAD))
        state.value = NftFilterModel.fromBundle(filtersBundle)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.CustomBottomSheetDialogTheme)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setupBottomSheet()
        return ComposeView(requireContext()).apply {
            setContent {
                FearlessTheme {
                    BottomSheetScreen {
                        val filtersState by state.collectAsStateWithLifecycle()
                        filtersState?.let {
                            FiltersContent(it, ::onSelectClick, ::onCloseClicked)
                        }
                    }
                }
            }
        }
    }

    private fun onSelectClick(model: NFTFilter) {
        val currentState = requireNotNull(state.value)
        val previousFilters = currentState.items.toMutableMap()
        val previousValue: Boolean = currentState.items[model] ?: false
        previousFilters[model] = !previousValue
        state.value = currentState.copy(items = previousFilters)
    }

    private fun onCloseClicked() {
        val result = requireNotNull(state.value)
        val resultDestinationId = requireNotNull(requireArguments().getInt(KEY_RESULT_DESTINATION_ID))
        navigator.setNftFiltersResult(KEY_RESULT, result, resultDestinationId )
        dismiss()
    }

    private fun setupBottomSheet() {
        dialog?.setOnShowListener {
            val bottomSheetDialog = it as BottomSheetDialog
            setupBehavior(bottomSheetDialog.behavior)
            bottomSheetDialog.setCanceledOnTouchOutside(false)
        }
    }

    private fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isDraggable = false
        behavior.isHideable = false
    }
}

val switchColors = object : SwitchColors {
    @Composable
    override fun thumbColor(enabled: Boolean, checked: Boolean): State<Color> {
        return rememberUpdatedState(white)
    }

    @Composable
    override fun trackColor(enabled: Boolean, checked: Boolean): State<Color> {
        return rememberUpdatedState(transparent)
    }
}

@Composable
private fun FiltersContent(
    state: NftFilterModel,
    onSelectClick: (NFTFilter) -> Unit,
    onCloseClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(42.dp)
        ) {
            H4(text = "Hide NFTs", modifier = Modifier.align(Alignment.TopCenter))
            Icon(
                painter = painterResource(id = R.drawable.ic_close_16_white_circle),
                tint = white,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onCloseClick)
                    .align(Alignment.TopEnd)
            )
        }
        MarginVertical(margin = 8.dp)
        state.items.forEach { (filter, checked) ->
            val trackColor = if (checked) colorAccent else black3
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .fillMaxWidth()
            ) {
                H4Bold(text = filter.name.uppercase(), modifier = Modifier.weight(1f))
                Switch(
                    colors = switchColors,
                    checked = checked,
                    onCheckedChange = { onSelectClick(filter) },
                    modifier = Modifier
                        .background(color = trackColor, shape = RoundedCornerShape(20.dp))
                        .padding(3.dp)
                        .height(20.dp)
                        .width(35.dp)
                )

            }
        }
    }
}

@Preview
@Composable
private fun FiltersContentPreview() {
    FearlessAppTheme {
        FiltersContent(NftFilterModel(NFTFilter.values().associateWith { true }), {}, {})
    }
}
