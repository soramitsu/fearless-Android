package jp.co.soramitsu.common.di

import android.content.Context
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.core.ResourceManager

interface CommonApi {

    fun context(): Context

    fun provideResourceManager(): ResourceManager

    fun provideNetworkApiCreator(): NetworkApiCreator
}