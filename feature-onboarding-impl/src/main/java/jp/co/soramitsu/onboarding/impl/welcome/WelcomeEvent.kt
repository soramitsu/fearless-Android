package jp.co.soramitsu.onboarding.impl.welcome

import jp.co.soramitsu.account.api.domain.model.AccountType

sealed interface WelcomeEvent {

    object AuthorizeGoogle : WelcomeEvent
    object ScanQR : WelcomeEvent
    object Back : WelcomeEvent

    sealed interface Onboarding: WelcomeEvent {
        val route: String

        object SplashScreen: Onboarding {
            override val route: String = "SplashScreen"
        }
        object PagerScreen: Onboarding {
            override val route: String = "PagerScreen"
        }
        class WelcomeScreen(val accountType: AccountType): Onboarding by Companion {
            companion object: Onboarding {
                override val route: String = "WelcomeScreen?accountType={accountType}"
            }
        }
        object SelectEcosystemScreen: Onboarding {
            override val route: String = "SelectEcosystemScreen"
        }

    }

}
