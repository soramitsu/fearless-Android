package jp.co.soramitsu.common.compose.theme.fw

import jp.co.soramitsu.common.BuildConfig

/** Independently reversible presentation surfaces. These flags never control wallet capabilities. */
enum class FwSurface(internal val requested: Boolean) {
    Recovery(BuildConfig.FW_RESTYLE_RECOVERY),
    Portfolio(BuildConfig.FW_RESTYLE_PORTFOLIO),
    ManageAssets(BuildConfig.FW_RESTYLE_MANAGE_ASSETS),
    Send(BuildConfig.FW_RESTYLE_SEND),
    Receive(BuildConfig.FW_RESTYLE_RECEIVE),
    Pickers(BuildConfig.FW_RESTYLE_PICKERS),
    Unavailable(BuildConfig.FW_RESTYLE_UNAVAILABLE),
    Signing(BuildConfig.FW_RESTYLE_SIGNING),
    Welcome(BuildConfig.FW_RESTYLE_WELCOME),
    Appearance(BuildConfig.FW_RESTYLE_APPEARANCE),
    Navigation(BuildConfig.FW_RESTYLE_NAVIGATION),
    Mnemonic(BuildConfig.FW_RESTYLE_MNEMONIC),
    Staking(BuildConfig.FW_RESTYLE_STAKING),
    Loading(BuildConfig.FW_RESTYLE_LOADING)
}
