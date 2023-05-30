package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import javax.inject.Inject

@HiltViewModel
class ImportRemoteAccountViewModel @Inject constructor() : BaseViewModel(), ImportRemoteAccountScreenInterface
