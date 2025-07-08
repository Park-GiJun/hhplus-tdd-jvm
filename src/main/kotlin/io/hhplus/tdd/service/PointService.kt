package io.hhplus.tdd.service

import io.hhplus.tdd.database.UserPointTable
import io.hhplus.tdd.dto.PointRequest
import io.hhplus.tdd.point.PointEvent
import io.hhplus.tdd.point.TransactionType
import io.hhplus.tdd.point.UserPoint
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service


@Service
class PointService(
    private val userPointTable: UserPointTable,
    private val pointEventPublisher: ApplicationEventPublisher
) {

    fun getUserPoint(id: Long): UserPoint {
        UserPoint.validateUserId(id)
        return userPointTable.selectById(id)
    }

    fun updateUserPoint(request: PointRequest): UserPoint {
        val currentUser = userPointTable.selectById(request.id)
        val updatedUser = when (request.transactionType) {
            TransactionType.CHARGE -> currentUser.charge(request.amount)
            TransactionType.USE -> currentUser.use(request.amount)
        }

        userPointTable.insertOrUpdate(request.id, updatedUser.point)

        pointEventPublisher.publishEvent(
            PointEvent(
                request.id, request.amount,
                currentUser.point, updatedUser.point, request.transactionType, System.currentTimeMillis()
            )
        )

        return updatedUser
    }
}
