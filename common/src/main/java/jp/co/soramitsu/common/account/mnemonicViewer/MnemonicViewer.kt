package jp.co.soramitsu.common.account.mnemonicViewer

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import jp.co.soramitsu.common.R
import kotlinx.android.synthetic.main.view_mnemonic.view.mnemonicViewerList

class MnemonicViewer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private val adapter = MnemonicWordsAdapter()

    init {
        View.inflate(context, R.layout.view_mnemonic, this)

        mnemonicViewerList.adapter = adapter
    }

    fun submitList(list: List<MnemonicWordModel>) {
        val manager = mnemonicViewerList.layoutManager as GridLayoutManager

        manager.spanCount = list.size / 2

        adapter.submitList(list)
    }
}