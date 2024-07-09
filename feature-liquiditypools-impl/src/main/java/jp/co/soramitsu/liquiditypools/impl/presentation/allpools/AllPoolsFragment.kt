package jp.co.soramitsu.liquiditypools.impl.presentation.allpools

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class AllPoolsFragment : BaseComposeBottomSheetDialogFragment<AllPoolsViewModel>() {

    override val viewModel: AllPoolsViewModel by viewModels()

    // Compose BackHandler does not work in DialogFragments, nor does BackPressedDispatcher
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            super.onCreateDialog(savedInstanceState).apply {
                setOnKeyListener { _, keyCode, event ->
                    val isBackPressDetected =
                        keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP

                    if (isBackPressDetected) {
                        viewModel.onNavigationClick()
                    }

                    return@setOnKeyListener isBackPressDetected
                }
            }
        } else {
            // Call to super.onBackPressed() will cancel dialog as default behavior
            object : BottomSheetDialog(requireContext(), theme) {
                @SuppressLint("MissingSuperCall")
                @RequiresApi(Build.VERSION_CODES.TIRAMISU)
                override fun onBackPressed() {
                    viewModel.onNavigationClick()
                }
            }
        }
    }

    @Composable
    override fun Content(padding: PaddingValues) {
        val navController = rememberNavController()

        SetupNavDestinationChangedListener(
            navController = navController,
            onNavDestinationChanged = remember {
                viewModel::onDestinationChanged
            }
        )

        AllPoolsNavRoot(
            viewModel = viewModel,
            )
    }

    @Composable
    private inline fun SetupNavDestinationChangedListener(
        navController: NavController,
        crossinline onNavDestinationChanged: (newRoute: String) -> Unit
    ) {
        val lifecycleOwner = LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner) {
            val onDestinationChangedListener =
                NavController.OnDestinationChangedListener { _, destination, _ ->
                    onNavDestinationChanged(destination.route!!)
                }

            val lifecycleObserver = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START ->
                        navController.addOnDestinationChangedListener(onDestinationChangedListener)

                    Lifecycle.Event.ON_STOP ->
                        navController.removeOnDestinationChangedListener(
                            onDestinationChangedListener
                        )

                    else -> Unit
                }
            }

            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
            onDispose { lifecycleOwner.lifecycle.removeObserver(lifecycleObserver) }
        }
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }
}
