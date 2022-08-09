package jp.co.soramitsu.featurecrowdloanimpl.di.validations

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module(
    includes = [
        ContributeValidationsModule::class
    ]
)
class CrowdloansValidationsModule
