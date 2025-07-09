package io.hhplus.tdd.dto

data class PointUpdateResponse(
    val userId: Long,
    val beforePointUpdate: Long,
    val afterPointUpdate: Long,
    val updatedMillis : Long
)
