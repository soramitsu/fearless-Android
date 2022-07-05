package jp.co.soramitsu.feature_account_impl.presentation.language

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseActivity
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.databinding.FragmentLanguagesBinding
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.language.model.LanguageModel

class LanguagesFragment : BaseFragment<LanguagesViewModel>(), LanguagesAdapter.LanguagesItemHandler {

    private lateinit var adapter: LanguagesAdapter

    private lateinit var binding: FragmentLanguagesBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLanguagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initViews() {
        adapter = LanguagesAdapter(this)

        with(binding) {
            languagesList.setHasFixedSize(true)
            languagesList.adapter = adapter

            fearlessToolbar.setHomeButtonListener {
                viewModel.backClicked()
            }
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .languagesComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: LanguagesViewModel) {
        adapter.submitList(viewModel.languagesModels)

        viewModel.selectedLanguageLiveData.observe(adapter::updateSelectedLanguage)

        viewModel.languageChangedEvent.observeEvent {
            (activity as BaseActivity<*>).changeLanguage()
        }
    }

    override fun checkClicked(languageModel: LanguageModel) {
        viewModel.selectLanguageClicked(languageModel)
    }
}
