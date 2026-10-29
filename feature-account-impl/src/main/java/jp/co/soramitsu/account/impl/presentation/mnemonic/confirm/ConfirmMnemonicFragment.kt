package jp.co.soramitsu.account.impl.presentation.mnemonic.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.impl.presentation.common.SecureScreenGuard
import jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.view.MnemonicWordView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.doOnGlobalLayout
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentConfirmMnemonicBinding

@AndroidEntryPoint
class ConfirmMnemonicFragment : BaseFragment<ConfirmMnemonicViewModel>() {

    companion object {
        const val KEY_PAYLOAD = "confirm_payload"

        fun getBundle(payload: ConfirmMnemonicPayload) = bundleOf(KEY_PAYLOAD to payload)
    }

    override val viewModel: ConfirmMnemonicViewModel by viewModels()

    private lateinit var binding: FragmentConfirmMnemonicBinding

    // The shuffled recovery phrase words must never reach a screenshot, a
    // recording, a cast or the recents thumbnail.
    private val secureScreen = SecureScreenGuard(this)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentConfirmMnemonicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        secureScreen.onViewCreated()
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        secureScreen.onStart()
    }

    override fun onStop() {
        secureScreen.onStop()
        super.onStop()
    }

    override fun onDestroyView() {
        secureScreen.onDestroyView()
        super.onDestroyView()
    }

    override fun initViews() {
        with(binding) {
            val actionSize = (48 * resources.displayMetrics.density).toInt()
            toolbar.findViewById<ImageView>(jp.co.soramitsu.common.R.id.backImg).apply {
                contentDescription = getString(R.string.ux_back)
                layoutParams = layoutParams.apply { width = actionSize; height = actionSize }
                scaleType = ImageView.ScaleType.CENTER
            }
            toolbar.findViewById<ImageView>(jp.co.soramitsu.common.R.id.rightImg).apply {
                contentDescription = getString(R.string.common_reset)
                layoutParams = layoutParams.apply { width = actionSize; height = actionSize }
                scaleType = ImageView.ScaleType.CENTER
            }
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
            MnemonicWordView(requireActivity()).apply {
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
        }
    }

    private val wordClickListener: (MnemonicWordView, String) -> Unit = { mnemonicWordView, word ->
        viewModel.addWordToConfirmMnemonic(word)

        binding.wordsMnemonicView.removeWordView(mnemonicWordView)

        val wordView = MnemonicWordView(requireActivity()).apply {
            setWord(word)
            setColorMode(MnemonicWordView.ColorMode.DARK)
            measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        }
        binding.confirmationMnemonicView.populateWord(wordView)
    }

    private fun playMatchingMnemonicErrorAnimation() {
        val animation = AnimationUtils.loadAnimation(requireActivity(), R.anim.shake)
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
