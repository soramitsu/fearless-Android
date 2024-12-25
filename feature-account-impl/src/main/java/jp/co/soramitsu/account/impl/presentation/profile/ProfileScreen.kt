package jp.co.soramitsu.account.impl.presentation.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.impl.presentation.backup_wallet.SettingsDivider
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.H1
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.SettingsItem
import jp.co.soramitsu.common.compose.component.SettingsItemAction
import jp.co.soramitsu.common.compose.component.WalletItem
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_account_impl.R

data class ProfileScreenState(
    val walletState: WalletItemViewState,
    val walletsItemAction: SettingsItemAction = SettingsItemAction.Transition,
    val currency: String,
    val language: String,
    val nomisChecked: Boolean,
    val soraCardVisible: Boolean,
)

interface ProfileScreenInterface {
    fun onWalletOptionsClick(item: WalletItemViewState)
    fun walletsClicked()

    fun onWalletConnectClick()
    fun onSoraCardClicked()
    fun currencyClicked()
    fun languagesClicked()

    fun onNomisMultichainScoreContainerClick()
    fun polkaswapDisclaimerClicked()
    fun changePinCodeClicked()

    fun aboutClicked()
    fun onScoreClick(item: WalletItemViewState)
}

@Composable
fun ProfileScreen(state: ProfileScreenState, callback: ProfileScreenInterface) {
    Column {
        MarginVertical(margin = 16.dp)
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            H1(text = stringResource(R.string.profile_settings_title))
            MarginVertical(margin = 16.dp)
            WalletItem(state = state.walletState, onOptionsClick = callback::onWalletOptionsClick, onScoreClick = callback::onScoreClick)
        }
        MarginVertical(margin = 16.dp)
        SettingsItem(icon = painterResource(R.drawable.ic_settings_wallets), text = stringResource(R.string.profile_wallets_title), action = state.walletsItemAction, onClick = callback::walletsClicked)
        SettingsDivider()
        SettingsItem(icon = painterResource(R.drawable.ic_wallet_connect), text = stringResource(R.string.profile_walletconnect_title), onClick = callback::onWalletConnectClick)
        SettingsDivider()
        if (state.soraCardVisible) {
            SettingsItem(
                icon = painterResource(R.drawable.ic_card),
                text = stringResource(R.string.profile_soracard_title),
                onClick = callback::onSoraCardClicked,
            )
            SettingsDivider()
        }
        SettingsItem(icon = painterResource(R.drawable.ic_dollar_circle), text = stringResource(R.string.common_currency), action = SettingsItemAction.Selector(state.currency), onClick = callback::currencyClicked)
        SettingsDivider()
        SettingsItem(icon = painterResource(R.drawable.ic_language), text = stringResource(R.string.profile_language_title), action = SettingsItemAction.Selector(state.language), onClick = callback::languagesClicked)
        SettingsDivider()
        SettingsItem(icon = painterResource(R.drawable.ic_score_star_full_24_pink), text = stringResource(R.string.profile_account_score_title), action = SettingsItemAction.Switch(state.nomisChecked), onClick = callback::onNomisMultichainScoreContainerClick)
        SettingsDivider()
        SettingsItem(icon = painterResource(R.drawable.ic_polkaswap_logo), text = stringResource(R.string.polkaswap_disclaimer_settings_item), onClick = callback::polkaswapDisclaimerClicked)
        SettingsDivider()
        SettingsItem(icon = painterResource(R.drawable.ic_pin_24), text = stringResource(R.string.profile_pincode_change_title), onClick = callback::changePinCodeClicked)
        SettingsDivider()
        SettingsItem(icon = painterResource(R.drawable.ic_info_primary_24), text = stringResource(R.string.about_title), onClick = callback::aboutClicked)
    }
}

@Composable
@Preview
fun ProfileScreenPreview() {
    val state = ProfileScreenState(
        WalletItemViewState(
            id = 111,
            balance = "44400.3",
            assetSymbol = "$",
            title = "My Wallet",
            walletIcon = jp.co.soramitsu.common.R.drawable.ic_wallet,
            isSelected = false,
            changeBalanceViewState = ChangeBalanceViewState(
                percentChange = "+5.67%",
                fiatChange = "$2345.32"
            ),
            score = 50
        ),
        currency = "USD",
        language = "ENG",
        nomisChecked = true,
        soraCardVisible = true,
    )
    FearlessAppTheme {
        ProfileScreen(state, object : ProfileScreenInterface {
            override fun onWalletOptionsClick(item: WalletItemViewState) = Unit
            override fun walletsClicked() = Unit
            override fun onWalletConnectClick() = Unit
            override fun onSoraCardClicked() = Unit
            override fun currencyClicked() = Unit
            override fun languagesClicked() = Unit
            override fun onNomisMultichainScoreContainerClick() = Unit
            override fun polkaswapDisclaimerClicked() = Unit
            override fun changePinCodeClicked() = Unit
            override fun aboutClicked() = Unit
            override fun onScoreClick(item: WalletItemViewState) = Unit
        })
    }
}