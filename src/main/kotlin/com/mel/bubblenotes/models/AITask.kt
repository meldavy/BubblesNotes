package com.mel.bubblenotes.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AITask(
    val id: Long,
    val noteId: Long,
    var status: Status = Status.PENDING,
    var result: JsonObject? = null,
    var errorMessage: String? = null,
    var startedAt: Long? = null,
    var completedAt: Long? = null,
) {
    enum class Status {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
    }
}
