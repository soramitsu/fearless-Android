package jp.co.soramitsu.common.mixin.api

import kotlinx.coroutines.flow.Flow

interface DemoMixin : DemoUi

interface DemoUi {
    val enableDemoWarningsFlow: Flow<Boolean>

    fun updateEnableDemoWarnings(enable: Boolean)
}
