package jp.co.soramitsu.account.impl.presentation.view.mnemonic

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
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
    }

    fun submitList(list: List<MnemonicWordModel>) {
        val manager = binding.mnemonicViewerList.layoutManager as GridLayoutManager

        manager.spanCount = list.size / 2

        adapter.submitList(list)
    }
}
