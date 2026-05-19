package de.ma.ftms.bridge.runtime

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import de.ma.ftms.core.storage.SqlDelightSessionHistoryRepository
import de.ma.ftms.core.storage.db.FtmsBridgeDatabase

internal fun createSessionHistoryRepository(context: Context): SqlDelightSessionHistoryRepository {
    val driver = AndroidSqliteDriver(
        schema = FtmsBridgeDatabase.Schema,
        context = context,
        name = "ftms_bridge_sessions.db",
    )
    return SqlDelightSessionHistoryRepository(driver)
}
