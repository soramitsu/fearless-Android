package jp.co.soramitsu.account.impl.presentation.experimental

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation.findNavController
import jp.co.soramitsu.feature_account_impl.R

class SuccessfulFragment : Fragment() {

    companion object {
        // todo shit
        var avatar: Drawable? = null
    }

    private val successAccountIcon: ImageView
        get() = requireView().findViewById(R.id.successAccountIcon)
    private val successBackButton: ImageView
        get() = requireView().findViewById(R.id.successBackButton)
    private val successRootView: View
        get() = requireView().findViewById(R.id.successRootView)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_succesful, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController(requireView())

        avatar?.let { successAccountIcon.setImageDrawable(it) }
        successAccountIcon.isVisible = avatar != null

        successBackButton.setOnClickListener {
            navController.navigateUp()
        }
        successRootView.setOnClickListener { navController.navigateUp() }
    }
}
