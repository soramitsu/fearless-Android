package jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network

import android.content.Context
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import kotlinx.android.synthetic.main.bottom_sheet_network_chooser.networkRv
import kotlinx.android.synthetic.main.bottom_sheet_network_chooser.titleTv

class NetworkChooserBottomSheetDialog(
    context: Context,
    nodes: List<NetworkModel>,
    itemClickListener: (NetworkModel) -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.bottom_sheet_network_chooser, null))
        titleTv.text = context.getString(R.string.common_choose_network)

        val adapter = NetworkAdapter {
            itemClickListener(it)
            dismiss()
        }

        adapter.submitList(nodes)
        networkRv.adapter = adapter
        networkRv.layoutManager = LinearLayoutManager(context)
    }
} 