package jp.co.soramitsu.liquiditypools.navigation

sealed interface NavAction {
    object BackPressed : NavAction
}