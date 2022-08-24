package jp.co.soramitsu.account.impl.presentation.language

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseActivity
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.feature_account_impl.databinding.FragmentLanguagesBinding
import jp.co.soramitsu.account.impl.presentation.language.model.LanguageModel

@AndroidEntryPoint
class LanguagesFragment : BaseFragment<LanguagesViewModel>(), LanguagesAdapter.LanguagesItemHandler {

    private lateinit var adapter: LanguagesAdapter

    private lateinit var binding: FragmentLanguagesBinding

    override val viewModel: LanguagesViewModel by viewModels()

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
