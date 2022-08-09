package jp.co.soramitsu.featurecrowdloanapi.di

import jp.co.soramitsu.featurecrowdloanapi.data.repository.CrowdloanRepository

interface CrowdloanFeatureApi {

    fun repository(): CrowdloanRepository
}
