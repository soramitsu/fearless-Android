package jp.co.soramitsu.feature_account_impl.presentation.language

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.language.model.LanguageModel
import kotlinx.android.synthetic.main.item_language.view.languageCheck

class LanguagesAdapter(
    private val languagesItemHandler: LanguagesItemHandler
) : ListAdapter<LanguageModel, LanguageViewHolder>(LanguagesDiffCallback) {

    interface LanguagesItemHandler {

        fun checkClicked(languageModel: LanguageModel)
    }

    private var selectedItem: LanguageModel? = null

    fun updateSelectedLanguage(newSelection: LanguageModel) {
        val positionToHide = selectedItem?.let { selected ->
            currentList.indexOfFirst { selected.iso == it.iso }
        }

        val positionToShow = currentList.indexOfFirst {
            newSelection.iso == it.iso
        }

        selectedItem = newSelection

        positionToHide?.let { notifyItemChanged(it) }
        notifyItemChanged(positionToShow)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): LanguageViewHolder {
        return LanguageViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.item_language, viewGroup, false))
    }

    override fun onBindViewHolder(languageViewHolder: LanguageViewHolder, position: Int) {
        val languageModel = getItem(position)
        val isChecked = languageModel.iso == selectedItem?.iso

        languageViewHolder.bind(languageModel, languagesItemHandler, isChecked)
    }
}

class LanguageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val languageNameTv: TextView = itemView.findViewById(R.id.languageNameTv)
    private val languageNativeNameTv: TextView = itemView.findViewById(R.id.languageNativeNameTv)

    fun bind(language: LanguageModel, handler: LanguagesAdapter.LanguagesItemHandler, isChecked: Boolean) {
        with(itemView) {
            languageNameTv.text = language.displayName
            languageNativeNameTv.text = language.nativeDisplayName

            languageCheck.visibility = if (isChecked) View.VISIBLE else View.INVISIBLE

            setOnClickListener { handler.checkClicked(language) }
        }
    }
}

object LanguagesDiffCallback : DiffUtil.ItemCallback<LanguageModel>() {
    override fun areItemsTheSame(oldItem: LanguageModel, newItem: LanguageModel): Boolean {
        return oldItem.iso == newItem.iso
    }

    override fun areContentsTheSame(oldItem: LanguageModel, newItem: LanguageModel): Boolean {
        return oldItem == newItem
    }
}