package jp.co.soramitsu.onboarding.impl.welcome

sealed interface WelcomeEvent {

    object AuthorizeGoogle : WelcomeEvent
}
