package jp.co.soramitsu.crowdloan.api.di

import jp.co.soramitsu.crowdloan.api.data.repository.CrowdloanRepository

interface CrowdloanFeatureApi {

    fun repository(): CrowdloanRepository
}
