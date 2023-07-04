package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.DropDown
import jp.co.soramitsu.common.compose.component.DropDownViewState
import jp.co.soramitsu.common.compose.component.ExapandableText
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MnemonicWordModel
import jp.co.soramitsu.common.compose.component.MnemonicWords
import jp.co.soramitsu.common.compose.component.TextInput
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.customColors

data class BackupMnemonicState(
    val mnemonicWords: List<MnemonicWordModel>,
    val selectedEncryptionType: String,
    val accountType: ImportAccountType,
    val substrateDerivationPath: String,
    val ethereumDerivationPath: String
) {
    companion object {
        val Empty = BackupMnemonicState(
            mnemonicWords = emptyList(),
            selectedEncryptionType = "",
            accountType = ImportAccountType.Substrate,
            substrateDerivationPath = "",
            ethereumDerivationPath = ""
        )
    }
}

interface BackupMnemonicCallback {

    fun onNextClick(
        launcher: ActivityResultLauncher<Intent>
    )

    fun onBackClick()

    fun onGoogleLoginError()

    fun onGoogleSignInSuccess()

    fun onSubstrateDerivationPathChange(path: String)

    fun onEthereumDerivationPathChange(path: String)

    fun chooseEncryptionClicked()
}

@Composable
internal fun BackupMnemonicContent(
    state: BackupMnemonicState,
    callback: BackupMnemonicCallback
) {
    Column {
        Toolbar(
            modifier = Modifier.padding(bottom = 12.dp),
            state = ToolbarViewState(
                title = stringResource(R.string.backup_mnemonic_title),
                navigationIcon = R.drawable.ic_arrow_back_24dp
            ),
            onNavigationClick = callback::onBackClick
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            B0(
                text = stringResource(R.string.backup_mnemonic_description),
                color = MaterialTheme.customColors.colorGreyText,
                textAlign = TextAlign.Center
            )

            MarginVertical(margin = 16.dp)

            MnemonicWords(
                mnemonicWords = state.mnemonicWords
            )

            MarginVertical(margin = 16.dp)

            ExapandableText(
                title = stringResource(id = R.string.common_advanced),
                initialState = false,
                content = {
                    DropDown(
                        state = DropDownViewState(
                            text = state.selectedEncryptionType,
                            hint = stringResource(R.string.substrate_crypto_type)
                        ),
                        onClick = callback::chooseEncryptionClicked
                    )
                    MarginVertical(margin = 12.dp)
                    TextInput(
                        state = TextInputViewState(
                            text = state.substrateDerivationPath,
                            hint = stringResource(R.string.substrate_secret_derivation_path)
                        ),
                        onInput = callback::onSubstrateDerivationPathChange
                    )
                    MarginVertical(margin = 8.dp)
                    B1(
                        text = stringResource(R.string.onboarding_substrate_derivation_path_hint),
                        color = MaterialTheme.customColors.colorGreyText
                    )
                    MarginVertical(margin = 12.dp)
                    DropDown(
                        state = DropDownViewState(
                            text = stringResource(R.string.ECDSA_crypto_type),
                            hint = stringResource(R.string.ethereum_crypto_type),
                            isActive = false
                        ),
                        onClick = {}
                    )
                    MarginVertical(margin = 12.dp)
                    TextInput(
                        state = TextInputViewState(
                            text = state.ethereumDerivationPath,
                            hint = stringResource(R.string.ethereum_secret_derivation_path)
                        ),
                        onInput = callback::onEthereumDerivationPathChange
                    )
                    MarginVertical(margin = 8.dp)
                    B1(
                        text = stringResource(R.string.onboarding_ethereum_derivation_path_hint),
                        color = MaterialTheme.customColors.colorGreyText
                    )
                    MarginVertical(margin = 24.dp)
                }
            )
        }

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                callback.onGoogleLoginError()
            } else {
                callback.onGoogleSignInSuccess()
            }
        }

        AccentButton(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .imePadding(),
            state = ButtonViewState(
                text = stringResource(R.string.import_remote_wallet_btn_create_wallet),
                enabled = true
            ),
            onClick = {
                callback.onNextClick(launcher)
            }
        )
        MarginVertical(12.dp)
    }
}
