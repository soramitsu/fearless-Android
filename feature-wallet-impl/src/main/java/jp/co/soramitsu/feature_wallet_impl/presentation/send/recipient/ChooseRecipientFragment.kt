package jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient.model.ContactModel
import kotlinx.android.synthetic.main.fragment_choose_recipient.searchRecipientFlipper

private const val INDEX_WELCOME = 0
private const val INDEX_CONTENT = 1
private const val INDEX_EMPTY = 2


class ChooseRecipientFragment : BaseFragment<ChooseRecipientViewModel>(), ChooseRecipientAdapter.NodeItemHandler {

    private lateinit var adapter: ChooseRecipientAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_choose_recipient, container, false)

    override fun initViews() {
        adapter = ChooseRecipientAdapter(this)


    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .chooseRecipientComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ChooseRecipientViewModel) {
        viewModel.screenStateLiveData.observe {
            val index = when(it) {
                State.WELCOME -> INDEX_WELCOME
                State.CONTENT -> INDEX_CONTENT
                State.EMPTY -> INDEX_EMPTY
            }

            searchRecipientFlipper.displayedChild = index
        }

        viewModel.searchResultLiveData.observe(adapter::submitList)
    }

    override fun contactClicked(contactModel: ContactModel) {
        viewModel.recipientSelected(contactModel.address)
    }
}