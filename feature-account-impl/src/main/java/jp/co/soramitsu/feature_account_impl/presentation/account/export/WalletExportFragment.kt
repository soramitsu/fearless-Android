package jp.co.soramitsu.feature_account_impl.presentation.account.export

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import coil.ImageLoader
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.databinding.FragmentWalletExportBinding
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.domain.account.details.AccountInChain
import javax.inject.Inject

private const val META_ID_KEY = "META_ID_KEY"

class WalletExportFragment : BaseFragment<WalletExportViewModel>() {

    @Inject
    lateinit var imageLoader: ImageLoader

    private lateinit var binding: FragmentWalletExportBinding

    companion object {
        fun getBundle(metaAccountId: Long) = bundleOf(META_ID_KEY to metaAccountId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) : View {
        binding = FragmentWalletExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initViews() {
        with(binding) {
            exportWalletToolbar.setHomeButtonListener {
                viewModel.backClicked()
            }
            continueBtn.setOnClickListener {
                if (amountsWithOneKeyView.isSelected) {
                    viewModel.continueClicked(AccountInChain.From.META_ACCOUNT)
                }
            }
        }
    }

    override fun inject() {
        val metaId = argument<Long>(META_ID_KEY)

        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .walletExportComponentFactory()
            .create(this, metaId)
            .inject(this)
    }

    override fun subscribe(viewModel: WalletExportViewModel) {
        viewModel.accountNameLiveData.observe {
            binding.exportWalletInfoView.setTitle(it)
        }
        viewModel.accountIconLiveData.observe {
            binding.exportWalletInfoView.setAccountIcon(it)
        }
        viewModel.totalBalanceLiveData.observe {
            binding.exportWalletInfoView.setText(it)
        }

        viewModel.amountsWithOneKeyAmountBadgeLiveData.observe {
            binding.amountsWithOneKeyView.setBadgeText(it)
        }

        viewModel.amountsWithOneKeyChainNameLiveData.observe {
            binding.amountsWithOneKeyView.setChainName(it)
        }

        viewModel.amountsWithOneKeyChainIconLiveData.observe {
            binding.amountsWithOneKeyView.loadChainIcon(it, imageLoader)
        }
    }
}
