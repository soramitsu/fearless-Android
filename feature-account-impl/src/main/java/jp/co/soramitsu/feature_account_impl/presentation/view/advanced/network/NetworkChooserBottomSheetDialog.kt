package jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network

import android.content.Context
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import kotlinx.android.synthetic.main.bottom_sheet_network_chooser.networkRv
import kotlinx.android.synthetic.main.bottom_sheet_network_chooser.titleTv

class NetworkChooserPayload(val networkModels: List<NetworkModel>, val selectedNetwork: NetworkModel)

class NetworkChooserBottomSheetDialog(
    context: Context,
    payload: NetworkChooserPayload,
    val itemClickListener: (NetworkModel) -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog), NetworkAdapter.NetworkItemHandler {

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.bottom_sheet_network_chooser, null))
        titleTv.text = context.getString(R.string.common_choose_network)

        val adapter = NetworkAdapter(this, payload.selectedNetwork)

        adapter.submitList(payload.networkModels)
        networkRv.adapter = adapter
        networkRv.layoutManager = LinearLayoutManager(context)
    }

    override fun onNetworkClicked(model: NetworkModel) {
        itemClickListener.invoke(model)
        dismiss()
    }
}