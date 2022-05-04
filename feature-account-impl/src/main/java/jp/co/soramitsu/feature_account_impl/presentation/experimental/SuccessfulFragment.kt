package jp.co.soramitsu.feature_account_impl.presentation.experimental

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.android.synthetic.main.fragment_succesful.successAccountIcon
import kotlinx.android.synthetic.main.fragment_succesful.successBackButton
import kotlinx.android.synthetic.main.fragment_succesful.successRootView

class SuccessfulFragment : Fragment() {

    companion object {
        // todo shit
        var avatar: Drawable? = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_succesful, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController()

        avatar?.let { successAccountIcon.setImageDrawable(it) }
        successAccountIcon.isVisible = avatar != null

        successBackButton.setOnClickListener {
            navController.navigateUp()
        }
        successRootView.setOnClickListener { navController.navigateUp() }
    }
}
