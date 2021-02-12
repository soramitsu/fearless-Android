package jp.co.soramitsu.core_runtime.di

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core_runtime.runtime.RuntimeUpdater
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot

interface RuntimeApi {

    fun provideRuntimeUpdater(): RuntimeUpdater

    fun provideRuntimeProperty(): SuspendableProperty<RuntimeSnapshot>
}