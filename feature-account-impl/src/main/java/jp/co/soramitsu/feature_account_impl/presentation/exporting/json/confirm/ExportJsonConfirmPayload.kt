package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class ExportJsonConfirmPayload(val address: String, val json: String) : Parcelable