package jp.co.soramitsu.feature_staking_impl.presentation.story

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.StakingStoryModel

class StoryViewModel(
    private val router: StakingRouter,
    private val story: StakingStoryModel
) : BaseViewModel() {

    private val _storyLiveData = MutableLiveData(story)
    val storyLiveData: LiveData<StakingStoryModel> = _storyLiveData

    fun backClicked() {
        router.back()
    }
}
