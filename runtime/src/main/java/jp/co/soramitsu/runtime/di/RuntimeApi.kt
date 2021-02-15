package jp.co.soramitsu.runtime.di

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.runtime.RuntimeUpdater

interface RuntimeApi {

    fun provideRuntimeUpdater(): RuntimeUpdater

    fun provideRuntimeProperty(): SuspendableProperty<RuntimeSnapshot>
}