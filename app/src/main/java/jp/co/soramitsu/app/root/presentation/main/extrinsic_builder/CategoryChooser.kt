package jp.co.soramitsu.app.root.presentation.main.extrinsic_builder

import android.content.Context
import android.os.Bundle
import android.view.View
import jp.co.soramitsu.app.R
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListSheetAdapter
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.HolderCreator
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.ReferentialEqualityDiffCallBack
import kotlinx.android.synthetic.main.item_sheet_category.view.itemSheetCategoryName

class CategoryChooser(
    context: Context,
    private val payload: CategoryChooserPayload,
) : DynamicListBottomSheet<String>(
    context,
    Payload(payload.categories),
    ReferentialEqualityDiffCallBack(),
    payload.onChosen
) {

    class CategoryChooserPayload(
        val chooserName: String,
        val categories: List<String>,
        val onChosen: (String) -> Unit,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(payload.chooserName)
    }

    override fun holderCreator(): HolderCreator<String> = {
        CategoryHolder(it.inflateChild(R.layout.item_sheet_category))
    }
}

private class CategoryHolder(view: View) : DynamicListSheetAdapter.Holder<String>(view) {

    override fun bind(item: String, isSelected: Boolean, handler: DynamicListSheetAdapter.Handler<String>) {
        super.bind(item, isSelected, handler)

        containerView.itemSheetCategoryName.text = item
    }
}


