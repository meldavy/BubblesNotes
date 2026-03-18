package com.mel.bubblenotes.repositories

import java.sql.ResultSet

fun ResultSet.getLongOrNull(columnName: String): Long? {
    val value = getLong(columnName)
    return if (wasNull()) null else value
}

fun ResultSet.getStringOrNull(columnName: String): String? {
    val value = getString(columnName)
    return if (wasNull()) null else value
}
