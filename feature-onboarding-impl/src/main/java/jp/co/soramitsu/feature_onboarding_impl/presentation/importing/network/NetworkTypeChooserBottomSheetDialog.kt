package jp.co.soramitsu.feature_onboarding_impl.presentation.importing.network

import android.app.Activity
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.presentation.importing.network.model.NodeModel
import kotlinx.android.synthetic.main.choosed_bottom_dialog.list
import kotlinx.android.synthetic.main.choosed_bottom_dialog.titleTv

class NetworkTypeChooserBottomSheetDialog(
    context: Activity,
    nodes: List<NodeModel>,
    itemClickListener: (NodeModel) -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {
    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.choosed_bottom_dialog, null))
        titleTv.text = context.getString(R.string.common_choose_network)

        val adapter = NodeListAdapter {
            itemClickListener(it)
            dismiss()
        }
        adapter.submitList(nodes)
        list.adapter = adapter
    }
}