package jp.co.soramitsu.soracard.impl.presentation.details

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.androidfoundation.intent.ShareUtil.shareText
import jp.co.soramitsu.androidfoundation.intent.getIntentForPackage
import jp.co.soramitsu.androidfoundation.intent.openGooglePlay
import jp.co.soramitsu.androidfoundation.intent.openSoraTelegramSupportChat
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.component.TextButton
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customButtonColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.soraBlueDialog
import jp.co.soramitsu.common.compose.theme.soracardDialog
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.feature_soracard_impl.R
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContract
import jp.co.soramitsu.oauth.uiscreens.clientsui.UiStyle
import jp.co.soramitsu.oauth.uiscreens.clientsui.localCompositionUiStyle
import jp.co.soramitsu.oauth.uiscreens.clientsui.soracarddetails.SoraCardDetailsCallback
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class SoraCardDetailsFragment : BaseComposeFragment<SoraCardDetailsViewModel>() {

    override val viewModel: SoraCardDetailsViewModel by viewModels()

    private val soraCardLauncher = registerForActivityResult(
        SoraCardContract()
    ) { }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.launchSoraCard.observe { contract ->
            soraCardLauncher.launch(contract)
        }
        viewModel.shareLinkEvent.observe { share ->
            context?.let { c ->
                shareText(c, getString(R.string.common_share), share)
            }
        }
        viewModel.telegramChat.observe {
            openSoraTelegramSupportChat(context)
        }
        viewModel.fiatWallet.observe {
            this@SoraCardDetailsFragment.context?.getIntentForPackage(it)?.let { intent ->
                startActivity(intent)
            }
        }
        viewModel.fiatWalletMarket.observe {
            this.context?.openGooglePlay(it)
        }
    }

    private val callback = object : SoraCardDetailsCallback {
        override fun onCloseReferralBannerClick() {
            viewModel.onCloseReferralBannerClick()
        }

        override fun onExchangeXorClick() {
            viewModel.onExchangeXorClick()
        }

        override fun onIbanCardClick() {
            viewModel.onIbanCardClick()
        }

        override fun onIbanCardShareClick() {
            viewModel.onIbanCardShareClick()
        }

        override fun onRecentActivityClick(position: Int) {
            viewModel.onRecentActivityClick(position)
        }

        override fun onReferralBannerClick() {
            viewModel.onReferralBannerClick()
        }

        override fun onSettingsOptionClick(position: Int) {
            viewModel.onSettingsOptionClick(
                position = position,
                context = context,
            )
        }

        override fun onShowMoreRecentActivitiesClick() {
            viewModel.onShowMoreRecentActivitiesClick()
        }
    }

    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) {
        FearlessAppTheme {
            val state = viewModel.soraCardDetailsScreenState.collectAsStateWithLifecycle().value
            CompositionLocalProvider(
                localCompositionUiStyle provides UiStyle.FW
            ) {
                SoraCardDetailsScreenInternal(
                    scrollState = scrollState,
                    state = state,
                    callback = callback,
                    onBack = viewModel::onBack,
                )
                if (state.logoutDialog) {
                    Dialog(
                        title = "Log out of SORA Card",
                        desc = "You are about to log out of SORA Card. You will still have access to the SORA Card standalone app, but the balance will no longer be available to you in the SORA Wallet.",
                        okButton = stringResource(R.string.profile_logout_title),
                        onCancel = viewModel::onLogoutDismiss,
                        onOk = viewModel::onSoraCardLogOutClick,
                    )
                }
                if (state.fiatWalletDialog) {
                    Dialog(
                        title = stringResource(id = jp.co.soramitsu.oauth.R.string.card_hub_manage_card_alert_title),
                        desc = stringResource(id = jp.co.soramitsu.oauth.R.string.card_hub_manage_google_play),
                        okButton = stringResource(R.string.common_ok),
                        onCancel = viewModel::onFiatWalletDismiss,
                        onOk = viewModel::onOpenFiatWalletMarket,
                    )
                }
            }
        }
    }
}

@Composable
private fun Dialog(
    title: String,
    desc: String,
    okButton: String,
    onCancel: () -> Unit,
    onOk: () -> Unit,
) {
    AlertDialog(
        backgroundColor = soracardDialog,
        onDismissRequest = onCancel,
        buttons = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(Dimens.x1),
                horizontalArrangement = Arrangement.spacedBy(Dimens.x2),
            ) {
                TextButton(
                    textStyle = MaterialTheme.customTypography.body1,
                    text = stringResource(R.string.common_cancel),
                    enabled = true,
                    colors = customButtonColors(Color.Unspecified, soraBlueDialog),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    textStyle = MaterialTheme.customTypography.body1,
                    text = okButton,
                    enabled = true,
                    colors = customButtonColors(Color.Unspecified, colorAccentDark),
                    onClick = onOk,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        title = {
            Text(
                color = white,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                text = title,
                style = MaterialTheme.customTypography.header3,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                color = white,
                text = desc,
                textAlign = TextAlign.Center,
            )
        },
    )
}

@Composable
@Preview(showBackground = true)
private fun PreviewDialog() {
    Dialog(
        title = "Title",
        desc = "You are about to log out of SORA Card. You will still have access to the SORA Card standalone app, but the balance will no longer be available to you in the SORA Wallet.",
        okButton = "Okay",
        onCancel = {},
        onOk = {},
    )
}
