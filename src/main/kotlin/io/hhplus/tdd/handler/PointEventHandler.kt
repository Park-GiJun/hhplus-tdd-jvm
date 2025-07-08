package io.hhplus.tdd.handler

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.point.PointEvent
import io.hhplus.tdd.point.TransactionType
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component


@Component
class PointHistoryEventHandler(
    private val pointHistoryTable: PointHistoryTable
) {
    @EventListener
    fun handle(event: PointEvent) {
        pointHistoryTable.insert(
            event.userId,
            event.amount,
            event.transactionType,
            event.timestamp
        )
    }
}