package jp.co.soramitsu.account.impl.presentation.view.mnemonic

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import jp.co.soramitsu.common.compose.component.MnemonicWordModel
import jp.co.soramitsu.common.compose.theme.fw.FwElevation
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwShape
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.fw.FwViews
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.ViewMnemonicBinding

class MnemonicViewer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private val adapter = MnemonicWordsAdapter()

    private val binding: ViewMnemonicBinding

    init {
        inflate(context, R.layout.view_mnemonic, this)
        binding = ViewMnemonicBinding.bind(this)

        binding.mnemonicViewerList.adapter = adapter
        if (FwRestyle.isEnabled(FwSurface.Mnemonic)) {
            // The words rest in one well pressed into the page.
            val padding = (12 * resources.displayMetrics.density).toInt()
            binding.mnemonicViewerList.background = FwViews.surface(context, FwElevation.Inset, FwShape.CARD.value)
            binding.mnemonicViewerList.setPadding(padding, padding, padding, padding)
        }
    }

    fun submitList(list: List<MnemonicWordModel>) {
        val manager = binding.mnemonicViewerList.layoutManager as GridLayoutManager

        manager.spanCount = (list.size / 2).coerceAtLeast(1)

        adapter.submitList(list)
    }
}
