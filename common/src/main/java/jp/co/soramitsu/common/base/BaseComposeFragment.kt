package jp.co.soramitsu.common.base

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.AlertDialog
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessTheme

abstract class BaseComposeFragment<T : BaseViewModel> : Fragment() {

    abstract val viewModel: T

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return ComposeView(requireContext()).apply {
            setContent {
                FearlessTheme {
                    val scaffoldState = rememberScaffoldState()
                    val scrollState = rememberScrollState()
                    val openAlertDialog = remember { mutableStateOf(AlertDialogData()) }

                    Scaffold(
                        scaffoldState = scaffoldState,
                        topBar = {
                            Column {
                                MarginVertical(margin = 24.dp) // it's status bar
                                Toolbar()
                            }
                        },
                        content = { padding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                            ) {
                                Content(padding, scrollState)

                                AlertDialogContent(openAlertDialog)
                                viewModel.errorLiveData.observeAsState().value?.let {
                                    openAlertDialog.value = AlertDialogData(
                                        title = stringResource(id = R.string.common_error_general_title),
                                        message = it.peekContent()
                                    )
                                }
                                viewModel.errorWithTitleLiveData.observeAsState().value?.let {
                                    val (title, message) = it.peekContent()

                                    openAlertDialog.value = AlertDialogData(
                                        title = title,
                                        message = message
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun AlertDialogContent(openAlertDialog: MutableState<AlertDialogData>) {
        if (openAlertDialog.value.title.isNotEmpty()) {
            AlertDialog(
                title = { Text(text = openAlertDialog.value.title) },
                text = { Text(text = openAlertDialog.value.message) },
                onDismissRequest = {
                    openAlertDialog.value = AlertDialogData()
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            openAlertDialog.value = AlertDialogData()
                        }
                    ) {
                        Text(text = stringResource(id = R.string.common_ok))
                    }
                }
            )
        }
    }

    @Composable
    abstract fun Content(padding: PaddingValues, scrollState: ScrollState)

    @Composable
    open fun Toolbar() = Unit

    fun <V> LiveData<V>.observe(observer: (V) -> Unit) {
        observe(viewLifecycleOwner, observer)
    }
}
