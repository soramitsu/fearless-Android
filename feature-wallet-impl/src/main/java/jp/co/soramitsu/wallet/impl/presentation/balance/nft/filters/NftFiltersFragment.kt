package jp.co.soramitsu.wallet.impl.presentation.balance.nft.filters

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.nft.domain.NFTInteractor

@AndroidEntryPoint
class NftFiltersFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var nftInteractor: NFTInteractor

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
                        val filtersState by nftInteractor.nftFiltersFlow()
                            .collectAsState(emptyMap())

                        FiltersContent(
                            state = filtersState,
                            onSelectClick = nftInteractor::setNFTFilter,
                            onCloseClick = ::dismiss
                        )
                    }
                }
            }
        }
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
