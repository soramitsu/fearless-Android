package jp.co.soramitsu.onboarding.impl.welcome

sealed interface WelcomeEvent {

    object AuthorizeGoogle : WelcomeEvent
    object ScanQR : WelcomeEvent
    sealed interface Onboarding: WelcomeEvent {
        val route: String

        object SplashScreen: Onboarding {
            override val route: String = "SplashScreen"
        }
        object PagerScreen: Onboarding {
            override val route: String = "PagerScreen"
        }
        object WelcomeScreen: Onboarding {
            override val route: String = "WelcomeScreen"
        }
    }

}
