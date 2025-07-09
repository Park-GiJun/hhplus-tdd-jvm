package io.hhplus.tdd.service

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import io.hhplus.tdd.dto.PointRequest
import io.hhplus.tdd.dto.PointUpdateResponse
import io.hhplus.tdd.point.PointEvent
import io.hhplus.tdd.point.PointHistory
import io.hhplus.tdd.point.TransactionType
import io.hhplus.tdd.point.UserPoint
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service


@Service
class PointService(
    private val userPointTable: UserPointTable,
    private val pointHistoryTable: PointHistoryTable,
    private val pointEventPublisher: ApplicationEventPublisher
) {

    fun getUserPoint(id: Long): UserPoint {
        UserPoint.validateUserId(id)
        return userPointTable.selectById(id)
    }

    fun updateUserPoint(request: PointRequest): PointUpdateResponse {
        val currentUser = userPointTable.selectById(request.id)
        val updatedUser = when (request.transactionType) {
            TransactionType.CHARGE -> currentUser.charge(request.amount)
            TransactionType.USE -> currentUser.use(request.amount)
        }

        val savedUserPoint = userPointTable.insertOrUpdate(request.id, updatedUser.point)

        pointEventPublisher.publishEvent(
            PointEvent(
                request.id, request.amount,
                currentUser.point, updatedUser.point, request.transactionType, System.currentTimeMillis()
            )
        )

        return PointUpdateResponse(
            userId = request.id,
            beforePointUpdate = currentUser.point,
            afterPointUpdate = savedUserPoint.point,
            updatedMillis = savedUserPoint.updateMillis
        )
    }

    fun selectAllByUserId(userId: Long): List<PointHistory> {
        return pointHistoryTable.selectAllByUserId(userId)
    }
}
