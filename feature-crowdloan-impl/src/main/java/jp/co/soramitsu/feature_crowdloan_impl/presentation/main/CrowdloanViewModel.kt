package jp.co.soramitsu.feature_crowdloan_impl.presentation.main

import androidx.lifecycle.liveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.CrowdloanInteractor

class CrowdloanViewModel(
    private val interactor: CrowdloanInteractor
) : BaseViewModel() {

    val crowdloansLiveData = liveData {
        emit(interactor.getAllCrowdloans())
    }
}
