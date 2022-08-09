package jp.co.soramitsu.account.impl.presentation.mnemonic.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.doOnGlobalLayout
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentConfirmMnemonicBinding
import jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.view.MnemonicWordView

@AndroidEntryPoint
class ConfirmMnemonicFragment : BaseFragment<ConfirmMnemonicViewModel>() {

    companion object {
        const val KEY_PAYLOAD = "confirm_payload"

        fun getBundle(payload: ConfirmMnemonicPayload) = bundleOf(KEY_PAYLOAD to payload)
    }

    override val viewModel: ConfirmMnemonicViewModel by viewModels()

    private lateinit var binding: FragmentConfirmMnemonicBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentConfirmMnemonicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initViews() {
        with(binding) {
            toolbar.setHomeButtonListener {
                viewModel.homeButtonClicked()
            }

            toolbar.setRightActionClickListener {
                viewModel.resetConfirmationClicked()
            }

            confirmationMnemonicView.setOnClickListener {
                viewModel.removeLastWordFromConfirmation()
            }

            confirmationMnemonicView.disableWordDisappearAnimation()

            nextBtn.setOnClickListener {
                viewModel.nextButtonClicked()
            }

            confirmMnemonicSkip.setOnClickListener {
                viewModel.skipClicked()
            }
        }
    }

    override fun subscribe(viewModel: ConfirmMnemonicViewModel) {
        binding.confirmMnemonicSkip.setVisible(viewModel.skipVisible)

        binding.wordsMnemonicView.doOnGlobalLayout {
            populateMnemonicContainer(viewModel.shuffledMnemonic)
        }

        viewModel.resetConfirmationEvent.observeEvent {
            binding.confirmationMnemonicView.resetView()
            binding.wordsMnemonicView.restoreAllWords()
        }

        viewModel.removeLastWordFromConfirmationEvent.observeEvent {
            binding.confirmationMnemonicView.removeLastWord()
            binding.wordsMnemonicView.restoreLastWord()
        }

        viewModel.nextButtonEnableLiveData.observe {
            binding.nextBtn.isEnabled = it
        }

        viewModel.skipButtonEnableLiveData.observe {
            binding.confirmMnemonicSkip.isEnabled = it
        }

        viewModel.matchingMnemonicErrorAnimationEvent.observeEvent {
            playMatchingMnemonicErrorAnimation()
        }
    }

    private fun populateMnemonicContainer(mnemonicWords: List<String>) {
        val words = mnemonicWords.map { mnemonicWord ->
            MnemonicWordView(activity!!).apply {
                setWord(mnemonicWord)
                setColorMode(MnemonicWordView.ColorMode.LIGHT)
                setOnClickListener { wordClickListener(this, mnemonicWord) }
                measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            }
        }

        with(binding) {
            wordsMnemonicView.populateWithMnemonic(words)

            val containerHeight = wordsMnemonicView.getMinimumMeasuredHeight()
            wordsMnemonicView.minimumHeight = containerHeight
            confirmationMnemonicView.minimumHeight = containerHeight
        }
    }

    private val wordClickListener: (MnemonicWordView, String) -> Unit = { mnemonicWordView, word ->
        viewModel.addWordToConfirmMnemonic(word)

        binding.wordsMnemonicView.removeWordView(mnemonicWordView)

        val wordView = MnemonicWordView(activity!!).apply {
            setWord(word)
            setColorMode(MnemonicWordView.ColorMode.DARK)
            measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        }
        binding.confirmationMnemonicView.populateWord(wordView)
    }

    private fun playMatchingMnemonicErrorAnimation() {
        val animation = AnimationUtils.loadAnimation(activity!!, R.anim.shake)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationRepeat(animation: Animation?) {
            }

            override fun onAnimationStart(animation: Animation?) {
            }

            override fun onAnimationEnd(animation: Animation?) {
                viewModel.matchingErrorAnimationCompleted()
            }
        })
        binding.confirmationMnemonicView.startAnimation(animation)
    }
}
