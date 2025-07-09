package io.hhplus.tdd.dto

import io.hhplus.tdd.point.TransactionType

data class PointRequest(
    val id: Long,
    val amount: Long,
    val transactionType: TransactionType
)