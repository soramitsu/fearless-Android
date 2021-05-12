package jp.co.soramitsu.core_db.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.model.Node

private const val PREFS_SELECTED_NODE = "node"

class PrefsToDbActiveNodeMigrator(
    val gson: Gson,
    val preferences: Preferences
) {

    fun migrate(db: SupportSQLiteDatabase) {
        val selectedNodeRaw = preferences.getString(PREFS_SELECTED_NODE) ?: return
        val selectedNode = gson.fromJson(selectedNodeRaw, Node::class.java)

        db.execSQL("UPDATE nodes SET isActive = 1 WHERE id = ${selectedNode.id}")
    }
}
