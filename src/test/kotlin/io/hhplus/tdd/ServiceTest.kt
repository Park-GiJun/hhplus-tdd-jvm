package io.hhplus.tdd

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import io.hhplus.tdd.dto.PointRequest
import io.hhplus.tdd.dto.PointUpdateResponse
import io.hhplus.tdd.point.PointEvent
import io.hhplus.tdd.point.PointHistory
import io.hhplus.tdd.point.TransactionType
import io.hhplus.tdd.point.UserPoint
import io.hhplus.tdd.service.PointService
import io.mockk.*
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher

@DisplayName("서비스 테스트")
class ServiceTest {
    private val logger = LoggerFactory.getLogger(ServiceTest::class.java)

    private lateinit var userPointTable: UserPointTable
    private lateinit var pointHistoryTable: PointHistoryTable
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var pointService: PointService

    @BeforeEach
    fun setUp() {
        userPointTable = mockk()
        pointHistoryTable = mockk()
        eventPublisher = mockk()
        pointService = PointService(userPointTable, pointHistoryTable, eventPublisher)
    }

    @Nested
    @DisplayName("사용자 포인트 조회")
    inner class GetUserPointTest {

        @Test
        @DisplayName("정상적인 사용자 ID로 포인트 조회시 UserPoint를 반환한다")
        fun getUserPoint_ValidUserId_ReturnsUserPoint() {
            val userId = 1L
            val expectedUserPoint = UserPoint(1L, 1000L, System.currentTimeMillis())

            every { userPointTable.selectById(userId) } returns expectedUserPoint

            logger.info("사용자 ID: {}", userId)

            val result = pointService.getUserPoint(userId)

            Assertions.assertThat(result).isEqualTo(expectedUserPoint)
            verify(exactly = 1) { userPointTable.selectById(userId) }

            logger.info("조회된 포인트: {}", result.point)
        }

//        @Test
//        @DisplayName("0의 사용자 ID로 조회시 예외가 발생한다")
//        fun getUserPoint_InvalidUserId_ThrowsException() {
//            val invalidUserId = 0L
//            val expectedMessage = "유저 아이디는 0보다 작을수 없습니다."
//
//            Assertions.assertThatThrownBy { pointService.getUserPoint(invalidUserId) }
//                .isInstanceOf(IllegalArgumentException::class.java)
//                .hasMessage(expectedMessage)
//
//            verify(exactly = 0) { userPointTable.selectById(any()) }
//        }
//
//        @Test
//        @DisplayName("음수 사용자 ID로 조회시 예외 발생")
//        fun getUserPoint_NegativeUserId_ThrowsException() {
//            val invalidUserId = -1L
//            val expectedMessage = "유저 아이디는 0보다 작을수 없습니다."
//
//            Assertions.assertThatThrownBy { pointService.getUserPoint(invalidUserId) }
//                .isInstanceOf(IllegalArgumentException::class.java)
//                .hasMessage(expectedMessage)
//
//            verify(exactly = 0) { userPointTable.selectById(any()) }
//        }

        @Test
        @DisplayName("존재하지 않는 사용자 ID로 조회시 기본값을 반환한다")
        fun getUserPoint_NonExistentUserId_ReturnsDefaultUserPoint() {
            val nonExistentUserId = 999L
            val defaultUserPoint = UserPoint(nonExistentUserId, 0L, System.currentTimeMillis())

            every { userPointTable.selectById(nonExistentUserId) } returns defaultUserPoint

            logger.info("존재하지 않는 사용자 ID: {}", nonExistentUserId)

            val result = pointService.getUserPoint(nonExistentUserId)

            Assertions.assertThat(result.id).isEqualTo(nonExistentUserId)
            Assertions.assertThat(result.point).isEqualTo(0L)
            verify(exactly = 1) { userPointTable.selectById(nonExistentUserId) }

            logger.info("기본값으로 생성된 포인트: {}", result.point)
        }
    }

    @Nested
    @DisplayName("사용자 포인트 히스토리 조회")
    inner class GetPointHistoryTest {

        @Test
        @DisplayName("정상적인 사용자 ID로 히스토리 조회시 List를 반환한다")
        fun selectAllByUserId_ValidUserId_ReturnsHistoryList() {
            val userId = 1L
            val expectedHistories = listOf(
                PointHistory(1L, userId, TransactionType.CHARGE, 1000L, System.currentTimeMillis()),
                PointHistory(2L, userId, TransactionType.USE, 500L, System.currentTimeMillis())
            )

            every { pointHistoryTable.selectAllByUserId(userId) } returns expectedHistories

            val result = pointService.selectAllByUserId(userId)

            Assertions.assertThat(result).hasSize(2)
            Assertions.assertThat(result).isEqualTo(expectedHistories)
            verify(exactly = 1) { pointHistoryTable.selectAllByUserId(userId) }
        }

        @Test
        @DisplayName("히스토리가 없는 사용자 ID로 조회시 빈 리스트를 반환한다")
        fun selectAllByUserId_NoHistory_ReturnsEmptyList() {
            val userId = 999L

            every { pointHistoryTable.selectAllByUserId(userId) } returns emptyList()

            val result = pointService.selectAllByUserId(userId)

            Assertions.assertThat(result).isEmpty()
            verify(exactly = 1) { pointHistoryTable.selectAllByUserId(userId) }
        }
    }

    @Nested
    @DisplayName("포인트 충전 테스트")
    inner class ChargePointTest {

        @Test
        @DisplayName("정상적인 포인트 충전")
        fun chargeUserPoint_ValidCharge_ReturnsPointUpdateResponse() {
            val chargeRequest = PointRequest(1L, 500L, TransactionType.CHARGE)
            val currentUserPoint = UserPoint(1L, 1000L, System.currentTimeMillis())
            val expectedPoint = 1500L
            val updateTime = System.currentTimeMillis()
            val updatedUserPoint = UserPoint(1L, expectedPoint, updateTime)

            every { userPointTable.selectById(1L) } returns currentUserPoint
            every { userPointTable.insertOrUpdate(1L, expectedPoint) } returns updatedUserPoint
            every { eventPublisher.publishEvent(any<PointEvent>()) } just Runs

            val result = pointService.updateUserPoint(chargeRequest)

            Assertions.assertThat(result).isInstanceOf(PointUpdateResponse::class.java)
            Assertions.assertThat(result.userId).isEqualTo(1L)
            Assertions.assertThat(result.beforePointUpdate).isEqualTo(1000L)
            Assertions.assertThat(result.afterPointUpdate).isEqualTo(expectedPoint)
            Assertions.assertThat(result.updatedMillis).isEqualTo(updateTime)

            verify(exactly = 1) { userPointTable.selectById(1L) }
            verify(exactly = 1) { userPointTable.insertOrUpdate(1L, expectedPoint) }

            verify(exactly = 1) {
                eventPublisher.publishEvent(
                    match<PointEvent> { event ->
                        event.userId == 1L &&
                                event.amount == 500L &&
                                event.beforePoint == 1000L &&
                                event.afterPoint == expectedPoint &&
                                event.transactionType == TransactionType.CHARGE
                    }
                )
            }
        }

//        @Test
//        @DisplayName("0원 충전시 예외 발생")
//        fun chargeUserPoint_ZeroAmount_ThrowsExceptionAndNoSideEffects() {
//            val invalidRequest = PointRequest(1L, 0L, TransactionType.CHARGE)
//            val currentUserPoint = UserPoint(1L, 1000L, System.currentTimeMillis())
//
//            every { userPointTable.selectById(1L) } returns currentUserPoint
//
//            Assertions.assertThatThrownBy { pointService.updateUserPoint(invalidRequest) }
//                .isInstanceOf(IllegalArgumentException::class.java)
//                .hasMessage("금액은 0보다 커야 합니다.")
//
//            verify(exactly = 1) { userPointTable.selectById(1L) }
//            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
//            verify(exactly = 0) { userPointTable.insertOrUpdate(any(), any()) }
//        }

        @Test
        @DisplayName("최대 포인트 초과 충전시 예외가 발생한다")
        fun chargeUserPoint_ExceedsMaxPoint_ThrowsException() {
            val overMaxRequest = PointRequest(1L, 2000L, TransactionType.CHARGE)
            val currentUserPoint = UserPoint(1L, 99_999_000L, System.currentTimeMillis())

            every { userPointTable.selectById(1L) } returns currentUserPoint

            Assertions.assertThatThrownBy { pointService.updateUserPoint(overMaxRequest) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("포인트가 최대값을 초과할 수 없습니다")

            verify(exactly = 1) { userPointTable.selectById(1L) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
            verify(exactly = 0) { userPointTable.insertOrUpdate(any(), any()) }
        }

//        @Test
//        @DisplayName("마이너스 포인트 충전")
//        fun chargeUserPoint_NegativeAmount_ThrowsException() {
//            val negativeRequest = PointRequest(1L, -100L, TransactionType.CHARGE)
//            val currentUserPoint = UserPoint(1L, 1000L, System.currentTimeMillis())
//
//            every { userPointTable.selectById(1L) } returns currentUserPoint
//
//            Assertions.assertThatThrownBy { pointService.updateUserPoint(negativeRequest) }
//                .isInstanceOf(IllegalArgumentException::class.java)
//                .hasMessage("금액은 0보다 커야 합니다.")
//
//            verify(exactly = 1) { userPointTable.selectById(1L) }
//            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
//            verify(exactly = 0) { userPointTable.insertOrUpdate(any(), any()) }
//        }
    }

    @Nested
    @DisplayName("포인트 사용 테스트")
    inner class UsePointTest {

        @Test
        @DisplayName("정상 포인트 사용")
        fun useUserPoint_ValidUse_ReturnsPointUpdateResponse() {
            val useRequest = PointRequest(1L, 500L, TransactionType.USE)
            val currentUserPoint = UserPoint(1L, 1000L, System.currentTimeMillis())
            val expectedPoint = 500L
            val updateTime = System.currentTimeMillis()
            val updatedUserPoint = UserPoint(1L, expectedPoint, updateTime)

            every { userPointTable.selectById(1L) } returns currentUserPoint
            every { userPointTable.insertOrUpdate(1L, expectedPoint) } returns updatedUserPoint
            every { eventPublisher.publishEvent(any<PointEvent>()) } just Runs

            val result = pointService.updateUserPoint(useRequest)

            Assertions.assertThat(result).isInstanceOf(PointUpdateResponse::class.java)
            Assertions.assertThat(result.userId).isEqualTo(1L)
            Assertions.assertThat(result.beforePointUpdate).isEqualTo(1000L)
            Assertions.assertThat(result.afterPointUpdate).isEqualTo(expectedPoint)
            Assertions.assertThat(result.updatedMillis).isEqualTo(updateTime)

            verify(exactly = 1) { userPointTable.selectById(1L) }
            verify(exactly = 1) { userPointTable.insertOrUpdate(1L, expectedPoint) }
            verify(exactly = 1) {
                eventPublisher.publishEvent(
                    match<PointEvent> { event ->
                        event.userId == 1L &&
                                event.amount == 500L &&
                                event.beforePoint == 1000L &&
                                event.afterPoint == expectedPoint &&
                                event.transactionType == TransactionType.USE
                    }
                )
            }
        }

        @Test
        @DisplayName("잔액 부족")
        fun useUserPoint_insufficientBalance_ThrowsException() {
            val useRequest = PointRequest(1L, 5000L, TransactionType.USE)
            val currentUserPoint = UserPoint(1L, 1000L, System.currentTimeMillis())

            every { userPointTable.selectById(1L) } returns currentUserPoint

            Assertions.assertThatThrownBy { pointService.updateUserPoint(useRequest) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("잔액이 부족합니다. 현재 포인트: 1000원, 사용 요청: 5000원")

            verify(exactly = 1) { userPointTable.selectById(1L) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
            verify(exactly = 0) { userPointTable.insertOrUpdate(any(), any()) }
        }

//        @Test
//        @DisplayName("0포인트 사용")
//        fun useUserPoint_ZeroPoint_ThrowException() {
//            val useRequest = PointRequest(1L, 0L, TransactionType.USE)
//            val currentUserPoint = UserPoint(1L, 1000L, System.currentTimeMillis())
//
//            every { userPointTable.selectById(1L) } returns currentUserPoint
//
//            Assertions.assertThatThrownBy { pointService.updateUserPoint(useRequest) }
//                .isInstanceOf(IllegalArgumentException::class.java)
//                .hasMessage("금액은 0보다 커야 합니다.")
//
//            verify(exactly = 1) { userPointTable.selectById(1L) }
//            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
//            verify(exactly = 0) { userPointTable.insertOrUpdate(any(), any()) }
//        }

//        @Test
//        @DisplayName("마이너스 포인트 사용")
//        fun useUserPoint_NegativeAmount_ThrowsException() {
//            val negativeRequest = PointRequest(1L, -100L, TransactionType.USE)
//            val currentUserPoint = UserPoint(1L, 1000L, System.currentTimeMillis())
//
//            every { userPointTable.selectById(1L) } returns currentUserPoint
//
//            Assertions.assertThatThrownBy { pointService.updateUserPoint(negativeRequest) }
//                .isInstanceOf(IllegalArgumentException::class.java)
//                .hasMessage("금액은 0보다 커야 합니다.")
//
//            verify(exactly = 1) { userPointTable.selectById(1L) }
//            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
//            verify(exactly = 0) { userPointTable.insertOrUpdate(any(), any()) }
//        }
    }
}