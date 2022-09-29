package jp.co.soramitsu.common.presentation

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import coil.ImageLoader
import coil.load
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.ClickHandler
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListSheetAdapter
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.HolderCreator

class FiatCurrenciesChooserBottomSheetDialog(
    context: Context,
    private val imageLoader: ImageLoader,
    payload: Payload<FiatCurrency>,
    clickHandler: ClickHandler<FiatCurrency>
) :
    DynamicListBottomSheet<FiatCurrency>(context, payload, FiatCurrencyDiffCallback, clickHandler) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.common_currency)
    }

    override fun holderCreator(): HolderCreator<FiatCurrency> = { parent ->
        FiatCurrencyHolder(parent.inflateChild(R.layout.item_fiat_chooser), imageLoader)
    }
}

private class FiatCurrencyHolder(parent: View, private val imageLoader: ImageLoader) : DynamicListSheetAdapter.Holder<FiatCurrency>(parent) {

    override fun bind(
        item: FiatCurrency,
        isSelected: Boolean,
        handler: DynamicListSheetAdapter.Handler<FiatCurrency>
    ) {
        super.bind(item, isSelected, handler)

        with(itemView) {
            findViewById<ImageView>(R.id.currencyIcon).load(item.icon, imageLoader) {
                this.size(resources.getDimension(R.dimen.x3).toInt())
            }
            findViewById<TextView>(R.id.currencyTitle).text = item.id.uppercase()
            findViewById<ImageView>(R.id.currencyChecked).visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
        }
    }
}

private object FiatCurrencyDiffCallback : DiffUtil.ItemCallback<FiatCurrency>() {
    override fun areItemsTheSame(oldItem: FiatCurrency, newItem: FiatCurrency): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: FiatCurrency, newItem: FiatCurrency): Boolean {
        return true
    }
}
