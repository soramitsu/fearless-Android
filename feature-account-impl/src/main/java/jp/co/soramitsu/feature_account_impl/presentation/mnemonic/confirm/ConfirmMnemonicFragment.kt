package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_confirm_mnemonic.wordsMnemonicView

class ConfirmMnemonicFragment : BaseFragment<ConfirmMnemonicViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_confirm_mnemonic, container, false)
    }

    override fun initViews() {
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(context!!, AccountFeatureApi::class.java)
            .confirmMnemonicComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ConfirmMnemonicViewModel) {
        observe(viewModel.mnemonicLiveData, Observer {
            wordsMnemonicView.populate(it)
        })
    }
}