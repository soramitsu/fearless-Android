package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.sign

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.fragment_raw_data.rawDataBackButton
import kotlinx.android.synthetic.main.fragment_raw_data.rawDataField

class TransactionRawDataFragment : Fragment() {

    companion object {
        private const val RAW_DATA_KEY = "rawDataKey"
        fun createBundle(rawData: String) = bundleOf(RAW_DATA_KEY to rawData)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_raw_data, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController()

        rawDataBackButton.setOnClickListener {
            navController.navigateUp()
        }

        arguments?.getString(RAW_DATA_KEY)?.let(rawDataField::setMessage) ?: navController.navigateUp()
    }
}
