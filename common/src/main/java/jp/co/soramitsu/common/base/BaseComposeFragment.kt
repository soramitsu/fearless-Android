package jp.co.soramitsu.common.base

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.EventObserver

abstract class BaseComposeFragment<T : BaseViewModel> : Fragment() {

    abstract val viewModel: T

    @OptIn(ExperimentalComposeUiApi::class)
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
                                    .semantics {
                                        testTagsAsResourceId = true
                                    }
                                    .fillMaxSize()
                                    .padding(padding)
                            ) {
                                Content(padding, scrollState)

                                AlertDialogContent(openAlertDialog)
                                val errorTitle = stringResource(id = R.string.common_error_general_title)
                                viewModel.errorLiveData.observeEvent {
                                    openAlertDialog.value = AlertDialogData(
                                        title = errorTitle,
                                        message = it
                                    )
                                }
                                viewModel.errorWithTitleLiveData.observeEvent { (title, message) ->
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.messageLiveData.observeEvent(::showMessage)
    }

    protected fun showMessage(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT)
            .show()
    }

    @Composable
    private fun AlertDialogContent(openAlertDialog: MutableState<AlertDialogData>) {
        if (openAlertDialog.value.title.isNotEmpty()) {
            AlertDialog(
                backgroundColor = backgroundBlack,
                title = { Text(text = openAlertDialog.value.title, color = white) },
                text = { Text(text = openAlertDialog.value.message, color = black2) },
                onDismissRequest = {
                    openAlertDialog.value = AlertDialogData()
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            openAlertDialog.value = AlertDialogData()
                        }
                    ) {
                        Text(text = stringResource(id = R.string.common_ok), color = white)
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

    inline fun <V> LiveData<Event<V>>.observeEvent(crossinline observer: (V) -> Unit) {
        observe(
            viewLifecycleOwner,
            EventObserver {
                observer.invoke(it)
            }
        )
    }
}
