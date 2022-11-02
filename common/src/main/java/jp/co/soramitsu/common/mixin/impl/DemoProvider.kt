package jp.co.soramitsu.common.mixin.impl

import jp.co.soramitsu.common.mixin.api.DemoMixin
import kotlinx.coroutines.flow.MutableStateFlow

class DemoProvider : DemoMixin {
    override val enableDemoWarningsFlow = MutableStateFlow(false)

    override fun updateEnableDemoWarnings(enable: Boolean) {
        enableDemoWarningsFlow.value = enable
    }
}
