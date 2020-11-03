package jp.co.soramitsu.common.account.mnemonicViewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.R
import kotlinx.android.synthetic.main.item_mnemonic_word.view.numberTv
import kotlinx.android.synthetic.main.item_mnemonic_word.view.wordTv

class MnemonicWordsAdapter : ListAdapter<MnemonicWordModel, MnemonicWordViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MnemonicWordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mnemonic_word, parent, false)
        return MnemonicWordViewHolder(view)
    }

    override fun onBindViewHolder(holder: MnemonicWordViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

object DiffCallback : DiffUtil.ItemCallback<MnemonicWordModel>() {

    override fun areItemsTheSame(oldItem: MnemonicWordModel, newItem: MnemonicWordModel): Boolean {
        return oldItem.numberToShow == newItem.numberToShow
    }

    override fun areContentsTheSame(oldItem: MnemonicWordModel, newItem: MnemonicWordModel): Boolean {
        return oldItem.word == newItem.word
    }
}

class MnemonicWordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    fun bind(mnemonicWord: MnemonicWordModel) {
        with(itemView) {
            numberTv.text = mnemonicWord.numberToShow
            wordTv.text = mnemonicWord.word
        }
    }
}