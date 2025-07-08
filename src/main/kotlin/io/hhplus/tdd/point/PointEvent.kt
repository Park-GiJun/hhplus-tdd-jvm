package io.hhplus.tdd.point

data class PointEvent(
    val userId: Long,
    val amount: Long,
    val beforePoint: Long,
    val afterPoint: Long,
    val transactionType: TransactionType,
    val timestamp : Long
)
