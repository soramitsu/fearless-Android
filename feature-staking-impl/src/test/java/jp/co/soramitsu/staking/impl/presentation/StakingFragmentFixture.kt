package jp.co.soramitsu.staking.impl.presentation

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import io.mockk.mockk
import java.math.BigDecimal
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import jp.co.soramitsu.wallet.api.presentation.model.FeeModel
import jp.co.soramitsu.staking.impl.presentation.validators.change.ValidatorModel
import jp.co.soramitsu.staking.impl.presentation.validators.change.CollatorModel
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.wallet.api.presentation.model.AssetModel
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltHostApplication::class)
abstract class StakingFragmentFixture : StakingViewFixture() {
    protected val asset = AssetModel("chain", "asset", "", "DOT", "100 DOT available")
    protected fun loadedFee() = FeeStatus.Loaded(FeeModel(BigDecimal.ONE, StakingLaneFixtures.token()))
    protected fun address(name: String) = AddressModel("address-$name", PictureDrawable(Picture()), name)

    protected val validatorRow = ValidatorModel("01", false, PictureDrawable(Picture()), "address",
        ValidatorModel.Scoring.OneField("12%"), "Alice", true, mockk())
    protected val collatorRow = CollatorModel("01", false, PictureDrawable(Picture()), "address",
        CollatorModel.Scoring.OneField("12%"), "Alice", true, mockk())

    @After
    fun closeFragmentHosts() {
        HiltFragmentHarness.closeHosts()
    }
}
