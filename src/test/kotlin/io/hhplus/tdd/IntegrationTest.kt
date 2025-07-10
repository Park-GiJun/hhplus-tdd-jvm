package io.hhplus.tdd

import com.fasterxml.jackson.databind.ObjectMapper
import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import io.hhplus.tdd.dto.PointAmountRequest
import io.hhplus.tdd.point.TransactionType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@DisplayName("통합 테스트")
class IntegrationTest {

    private val logger = LoggerFactory.getLogger(IntegrationTest::class.java)

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userPointTable: UserPointTable

    @Autowired
    private lateinit var pointHistoryTable: PointHistoryTable

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        logger.info("테스트 셋업 시작")
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()

        clear()
        logger.info("테스트 셋업 완료")
    }

    private fun clear() {
        logger.info("테이블 초기화 시작")

        val userTableField = UserPointTable::class.java.getDeclaredField("table")
        userTableField.isAccessible = true
        val userTable = userTableField.get(userPointTable) as HashMap<*, *>
        val userTableSizeBefore = userTable.size
        userTable.clear()
        logger.info("UserPointTable 초기화: {} -> 0개", userTableSizeBefore)

        val historyTableField = PointHistoryTable::class.java.getDeclaredField("table")
        historyTableField.isAccessible = true
        val historyTable = historyTableField.get(pointHistoryTable) as MutableList<*>
        val historyTableSizeBefore = historyTable.size
        historyTable.clear()
        logger.info("PointHistoryTable 초기화: {} -> 0개", historyTableSizeBefore)

        val cursorField = PointHistoryTable::class.java.getDeclaredField("cursor")
        cursorField.isAccessible = true
        cursorField.set(pointHistoryTable, 1L)
        logger.info("PointHistoryTable cursor 초기화: 1로 설정")
    }

    @Test
    @DisplayName("포인트 조회 - 존재하지 않는 사용자")
    fun getPoint_InvalidUser() {
        val userId = 1L
        logger.info("포인트 조회 테스트 시작: userId={}", userId)

        logger.info("HTTP GET 요청 시작: /point/{}", userId)
        mockMvc.perform(MockMvcRequestBuilders.get("/point/{id}", userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.point").value(0))
        logger.info("HTTP 응답 검증 완료: 200 OK, point=0")

        val userPoint = userPointTable.selectById(userId)
        logger.info(
            "실제 테이블 조회 결과: id={}, point={}, updateMillis={}",
            userPoint.id, userPoint.point, userPoint.updateMillis
        )

        assert(userPoint.point == 0L)
    }

    @Test
    @DisplayName("포인트 충전 - 정상")
    fun chargePoint_Success() {
        val userId = 1L
        val chargeAmount = 1000L
        logger.info("포인트 충전 테스트 시작: userId={}, amount={}", userId, chargeAmount)

        val beforePoint = userPointTable.selectById(userId)
        logger.info("충전 전 포인트: {}", beforePoint.point)

        val request = PointAmountRequest(chargeAmount)
        logger.info("충전 요청 생성: amount={}", request.amount)

        logger.info("HTTP PATCH 요청 시작: /point/{}/charge", userId)
        mockMvc.perform(
            MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.beforePointUpdate").value(0L))
            .andExpect(jsonPath("$.afterPointUpdate").value(chargeAmount))
        logger.info("HTTP 응답 검증 완료: 200 OK, before=0, after={}", chargeAmount)

        val updatedPoint = userPointTable.selectById(userId)
        logger.info("충전 후 실제 포인트: id={}, point={}", updatedPoint.id, updatedPoint.point)
        assert(updatedPoint.point == chargeAmount)

        val histories = pointHistoryTable.selectAllByUserId(userId)
        logger.info("히스토리 개수: {}", histories.size)
        histories.forEachIndexed { index, history ->
            logger.info(
                "히스토리[{}]: id={}, userId={}, amount={}, type={}, timeMillis={}",
                index, history.id, history.userId, history.amount, history.type, history.timeMillis
            )
        }

        assert(histories.size == 1)
        assert(histories[0].amount == chargeAmount)
        assert(histories[0].type == TransactionType.CHARGE)
    }

    @Test
    @DisplayName("포인트 사용 - 정상")
    fun usePoint_Success() {
        val userId = 1L
        val initialPoint = 2000L
        val useAmount = 500L
        logger.info(
            "포인트 사용 테스트 시작: userId={}, initial={}, use={}",
            userId, initialPoint, useAmount
        )

        logger.info("초기 포인트 설정 시작: {}원", initialPoint)
        userPointTable.insertOrUpdate(userId, initialPoint)
        val setPoint = userPointTable.selectById(userId)
        logger.info("초기 포인트 설정 완료: 실제값={}", setPoint.point)

        val request = PointAmountRequest(useAmount)
        logger.info("사용 요청 생성: amount={}", request.amount)

        logger.info("HTTP PATCH 요청 시작: /point/{}/use", userId)
        mockMvc.perform(
            MockMvcRequestBuilders.patch("/point/{id}/use", userId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.beforePointUpdate").value(initialPoint))
            .andExpect(jsonPath("$.afterPointUpdate").value(initialPoint - useAmount))
        logger.info(
            "HTTP 응답 검증 완료: 200 OK, before={}, after={}",
            initialPoint, initialPoint - useAmount
        )

        val updatedPoint = userPointTable.selectById(userId)
        logger.info("사용 후 실제 포인트: id={}, point={}", updatedPoint.id, updatedPoint.point)
        assert(updatedPoint.point == initialPoint - useAmount)

        val histories = pointHistoryTable.selectAllByUserId(userId)
        logger.info("히스토리 개수: {}", histories.size)
        histories.forEachIndexed { index, history ->
            logger.info(
                "히스토리[{}]: id={}, userId={}, amount={}, type={}, timeMillis={}",
                index, history.id, history.userId, history.amount, history.type, history.timeMillis
            )
        }

        assert(histories.size == 1)
        assert(histories[0].type == TransactionType.USE)
    }

    @Test
    @DisplayName("시나리오 - 충전 -> 사용 -> 충전 -> 충전 -> 조회 -> 히스토리 조회")
    fun singleUser_Success() {
        val userId = 1L
        logger.info("시나리오 1 테스트 시작: userId={}", userId)

        logger.info("1단계: 첫 번째 충전 (1000원)")
        val charge1 = 1000L
        mockMvc.perform(
            MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(charge1)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.beforePointUpdate").value(0L))
            .andExpect(jsonPath("$.afterPointUpdate").value(charge1))

        val afterCharge1 = userPointTable.selectById(userId)
        logger.info("1단계 완료: 현재 포인트 = {}원", afterCharge1.point)
        assert(afterCharge1.point == charge1)

        logger.info("2단계: 포인트 사용 (300원)")
        val use1 = 300L
        mockMvc.perform(
            MockMvcRequestBuilders.patch("/point/{id}/use", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(use1)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.beforePointUpdate").value(charge1))
            .andExpect(jsonPath("$.afterPointUpdate").value(charge1 - use1))

        val afterUse1 = userPointTable.selectById(userId)
        logger.info("2단계 완료: 현재 포인트 = {}원", afterUse1.point)
        assert(afterUse1.point == charge1 - use1)

        logger.info("3단계: 두 번째 충전 (2000원)")
        val charge2 = 2000L
        val beforeCharge2 = charge1 - use1
        mockMvc.perform(
            MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(charge2)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.beforePointUpdate").value(beforeCharge2))
            .andExpect(jsonPath("$.afterPointUpdate").value(beforeCharge2 + charge2))

        val afterCharge2 = userPointTable.selectById(userId)
        logger.info("3단계 완료: 현재 포인트 = {}원", afterCharge2.point)
        assert(afterCharge2.point == beforeCharge2 + charge2)

        logger.info("4단계: 세 번째 충전 (500원)")
        val charge3 = 500L
        val beforeCharge3 = beforeCharge2 + charge2
        mockMvc.perform(
            MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(charge3)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.beforePointUpdate").value(beforeCharge3))
            .andExpect(jsonPath("$.afterPointUpdate").value(beforeCharge3 + charge3))

        val afterCharge3 = userPointTable.selectById(userId)
        val expectedFinalPoint = 3200L
        logger.info("4단계 완료: 현재 포인트 = {}원", afterCharge3.point)
        assert(afterCharge3.point == expectedFinalPoint)

        logger.info("5단계: 포인트 조회")
        mockMvc.perform(MockMvcRequestBuilders.get("/point/{id}", userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.point").value(expectedFinalPoint))

        logger.info("5단계 완료: 조회된 포인트 = {}원", expectedFinalPoint)

        logger.info("6단계: 히스토리 조회")
        mockMvc.perform(MockMvcRequestBuilders.get("/point/{id}/histories", userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(4))

        val histories = pointHistoryTable.selectAllByUserId(userId)
        logger.info("총 히스토리 개수: {}건", histories.size)

        val expectedHistory = listOf(
            Pair(TransactionType.CHARGE, charge1),
            Pair(TransactionType.USE, use1),
            Pair(TransactionType.CHARGE, charge2),
            Pair(TransactionType.CHARGE, charge3)
        )

        histories.forEachIndexed { index, history ->
            logger.info(
                "히스토리[{}]: type={}, amount={}원, timeMillis={}",
                index + 1, history.type, history.amount, history.timeMillis
            )

            assert(history.type == expectedHistory[index].first)
            assert(history.amount == expectedHistory[index].second)
        }

        assert(histories.size == 4)
        assert(afterCharge3.point == expectedFinalPoint)
    }

    @Test
    @DisplayName("다중 사용자 랜덤 요청 테스트")
    fun multiUserRandomScenario_Success() {
        val userIds = listOf(1L, 2L, 3L, 4L)
        val repeatCount = 5
        val random = kotlin.random.Random

        logger.info("다중 사용자 랜덤 테스트 시작")
        logger.info("사용자: {}, 각자 {}번씩 랜덤 거래", userIds, repeatCount)

        userIds.forEach { userId ->
            logger.info("사용자 {} 랜덤 거래 시작", userId)

            repeat(repeatCount) { round ->
                try {
                    val sleepTime = random.nextInt(400) + 100
                    Thread.sleep(sleepTime.toLong())
                    logger.info("사용자 {} - {}회차: {}ms 대기 후 거래 시작", userId, round + 1, sleepTime)

                    val currentPoint = userPointTable.selectById(userId).point
                    logger.info("사용자 {} - {}회차: 현재 포인트 {}원", userId, round + 1, currentPoint)

                    val isCharge = random.nextBoolean()

                    if (isCharge) {
                        val chargeAmount = (random.nextInt(16) + 5) * 100L
                        logger.info("사용자 {} - {}회차: {}원 충전 시도", userId, round + 1, chargeAmount)

                        mockMvc.perform(
                            MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(PointAmountRequest(chargeAmount)))
                        ).andExpect(status().isOk)

                        logger.info("사용자 {} - {}회차: {}원 충전 성공", userId, round + 1, chargeAmount)

                    } else {
                        if (currentPoint > 0) {
                            val maxUse = (currentPoint * 0.9).toLong()
                            val minUse = maxOf(100L, (currentPoint * 0.1).toLong())

                            if (maxUse >= minUse) {
                                val useAmount = random.nextLong(maxUse - minUse + 1) + minUse
                                logger.info(
                                    "사용자 {} - {}회차: {}원 사용 시도 (보유: {}원)",
                                    userId,
                                    round + 1,
                                    useAmount,
                                    currentPoint
                                )

                                mockMvc.perform(
                                    MockMvcRequestBuilders.patch("/point/{id}/use", userId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(PointAmountRequest(useAmount)))
                                ).andExpect(status().isOk)

                                logger.info("사용자 {} - {}회차: {}원 사용 성공", userId, round + 1, useAmount)
                            } else {
                                logger.info("사용자 {} - {}회차: 포인트 부족으로 충전으로 변경", userId, round + 1)
                                val chargeAmount = 1000L
                                mockMvc.perform(
                                    MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(PointAmountRequest(chargeAmount)))
                                ).andExpect(status().isOk)
                                logger.info("사용자 {} - {}회차: {}원 충전 성공 (대체)", userId, round + 1, chargeAmount)
                            }
                        } else {
                            logger.info("사용자 {} - {}회차: 포인트 0원으로 충전으로 변경", userId, round + 1)
                            val chargeAmount = 1000L
                            mockMvc.perform(
                                MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(PointAmountRequest(chargeAmount)))
                            ).andExpect(status().isOk)
                            logger.info("사용자 {} - {}회차: {}원 충전 성공 (대체)", userId, round + 1, chargeAmount)
                        }
                    }

                } catch (e: Exception) {
                    logger.error("사용자 {} - {}회차: 거래 실패 - {}", userId, round + 1, e.message)
                }
            }

            logger.info("사용자 {} 랜덤 거래 완료", userId)
        }

        logger.info("최종 결과 검증")
        userIds.forEach { userId ->
            val finalPoint = userPointTable.selectById(userId)
            val histories = pointHistoryTable.selectAllByUserId(userId)

            logger.info("사용자 {} 최종 결과:", userId)
            logger.info("  - 최종 포인트: {}원", finalPoint.point)
            logger.info("  - 총 거래 횟수: {}회", histories.size)

            var totalCharge = 0L
            var totalUse = 0L
            var chargeCount = 0
            var useCount = 0

            histories.forEach { history ->
                when (history.type) {
                    TransactionType.CHARGE -> {
                        totalCharge += history.amount
                        chargeCount++
                    }

                    TransactionType.USE -> {
                        totalUse += history.amount
                        useCount++
                    }
                }
            }

            logger.info("  - 총 충전: {}원 ({}회)", totalCharge, chargeCount)
            logger.info("  - 총 사용: {}원 ({}회)", totalUse, useCount)
            logger.info(
                "  - 계산 검증: {} - {} = {} (실제: {})",
                totalCharge,
                totalUse,
                totalCharge - totalUse,
                finalPoint.point
            )

            assert(finalPoint.point == totalCharge - totalUse) {
                "사용자 $userId 포인트 계산 오류: 예상=${totalCharge - totalUse}, 실제=${finalPoint.point}"
            }

            assert(histories.size >= 3) {
                "사용자 $userId 거래 횟수 부족: ${histories.size}회"
            }

            assert(finalPoint.point >= 0) {
                "사용자 $userId 포인트가 음수: ${finalPoint.point}"
            }

            logger.info("사용자 {} 검증 완료", userId)
        }
    }

    @Test
    @DisplayName("다중 사용자 랜덤 요청 테스트 (예외 케이스 포함)")
    fun multiUserRandomScenario_WithExceptions() {
        val userIds = listOf(1L, 2L, 3L, 4L)
        val repeatCount = 5
        val random = kotlin.random.Random

        logger.info("다중 사용자 랜덤 테스트 시작 (예외 케이스 포함)")
        logger.info("사용자: {}, 각자 {}번씩 랜덤 거래", userIds, repeatCount)

        val userStats = mutableMapOf<Long, MutableMap<String, Int>>()
        userIds.forEach { userId ->
            userStats[userId] = mutableMapOf(
                "success" to 0,
                "failed" to 0,
                "charge" to 0,
                "use" to 0,
                "exception" to 0
            )
        }

        userIds.forEach { userId ->
            logger.info("사용자 {} 랜덤 거래 시작", userId)

            repeat(repeatCount) { round ->
                try {
                    val sleepTime = random.nextInt(400) + 100
                    Thread.sleep(sleepTime.toLong())
                    logger.info("사용자 {} - {}회차: {}ms 대기 후 거래 시작", userId, round + 1, sleepTime)

                    val currentPoint = userPointTable.selectById(userId).point
                    logger.info("사용자 {} - {}회차: 현재 포인트 {}원", userId, round + 1, currentPoint)

                    val actionType = when (random.nextInt(10)) {
                        0, 1, 2 -> "exception"
                        3, 4 -> "use"
                        else -> "charge"
                    }

                    when (actionType) {
                        "charge" -> {
                            val chargeAmount = (random.nextInt(16) + 5) * 100L // 500~2000원
                            logger.info("사용자 {} - {}회차: {}원 충전 시도", userId, round + 1, chargeAmount)

                            mockMvc.perform(
                                MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(PointAmountRequest(chargeAmount)))
                            ).andExpect(status().isOk)

                            userStats[userId]!!["success"] = userStats[userId]!!["success"]!! + 1
                            userStats[userId]!!["charge"] = userStats[userId]!!["charge"]!! + 1
                            logger.info("사용자 {} - {}회차: {}원 충전 성공", userId, round + 1, chargeAmount)
                        }

                        "use" -> {
                            if (currentPoint > 0) {
                                val maxUse = (currentPoint * 0.9).toLong()
                                val minUse = maxOf(100L, (currentPoint * 0.1).toLong())

                                if (maxUse >= minUse) {
                                    val useAmount = random.nextLong(maxUse - minUse + 1) + minUse
                                    logger.info(
                                        "사용자 {} - {}회차: {}원 사용 시도 (보유: {}원)",
                                        userId,
                                        round + 1,
                                        useAmount,
                                        currentPoint
                                    )

                                    mockMvc.perform(
                                        MockMvcRequestBuilders.patch("/point/{id}/use", userId)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(objectMapper.writeValueAsString(PointAmountRequest(useAmount)))
                                    ).andExpect(status().isOk)

                                    userStats[userId]!!["success"] = userStats[userId]!!["success"]!! + 1
                                    userStats[userId]!!["use"] = userStats[userId]!!["use"]!! + 1
                                    logger.info("사용자 {} - {}회차: {}원 사용 성공", userId, round + 1, useAmount)
                                } else {
                                    logger.info("사용자 {} - {}회차: 포인트 부족으로 충전으로 변경", userId, round + 1)
                                    val chargeAmount = 1000L
                                    mockMvc.perform(
                                        MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(objectMapper.writeValueAsString(PointAmountRequest(chargeAmount)))
                                    ).andExpect(status().isOk)

                                    userStats[userId]!!["success"] = userStats[userId]!!["success"]!! + 1
                                    userStats[userId]!!["charge"] = userStats[userId]!!["charge"]!! + 1
                                    logger.info("사용자 {} - {}회차: {}원 충전 성공 (대체)", userId, round + 1, chargeAmount)
                                }
                            } else {
                                logger.info("사용자 {} - {}회차: 포인트 0원으로 충전으로 변경", userId, round + 1)
                                val chargeAmount = 1000L
                                mockMvc.perform(
                                    MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(PointAmountRequest(chargeAmount)))
                                ).andExpect(status().isOk)

                                userStats[userId]!!["success"] = userStats[userId]!!["success"]!! + 1
                                userStats[userId]!!["charge"] = userStats[userId]!!["charge"]!! + 1
                                logger.info("사용자 {} - {}회차: {}원 충전 성공 (대체)", userId, round + 1, chargeAmount)
                            }
                        }

                        "exception" -> {
                            val exceptionCase = random.nextInt(4)

                            when (exceptionCase) {
                                0 -> {
                                    val excessiveAmount = currentPoint + random.nextInt(1000) + 500L
                                    logger.info(
                                        "사용자 {} - {}회차: 과다 사용 시도 {}원 (보유: {}원) - 예외 예상",
                                        userId, round + 1, excessiveAmount, currentPoint
                                    )

                                    val result = mockMvc.perform(
                                        MockMvcRequestBuilders.patch("/point/{id}/use", userId)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(objectMapper.writeValueAsString(PointAmountRequest(excessiveAmount)))
                                    )

                                    if (currentPoint < excessiveAmount) {
                                        result.andExpect(status().isBadRequest)
                                        userStats[userId]!!["failed"] = userStats[userId]!!["failed"]!! + 1
                                        logger.info("사용자 {} - {}회차: 과다 사용 실패 - 예외", userId, round + 1)
                                    } else {
                                        result.andExpect(status().isOk)
                                        userStats[userId]!!["success"] = userStats[userId]!!["success"]!! + 1
                                        userStats[userId]!!["use"] = userStats[userId]!!["use"]!! + 1
                                        logger.info("사용자 {} - {}회차: 사용 성공 (예상과 다름)", userId, round + 1)
                                    }
                                }

                                1 -> {
                                    val negativeAmount = -(random.nextInt(1000) + 100L)
                                    logger.info(
                                        "사용자 {} - {}회차: 음수 금액 충전 시도 {}원 - 예외",
                                        userId, round + 1, negativeAmount
                                    )

                                    mockMvc.perform(
                                        MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(objectMapper.writeValueAsString(PointAmountRequest(negativeAmount)))
                                    ).andExpect(status().isBadRequest)

                                    userStats[userId]!!["failed"] = userStats[userId]!!["failed"]!! + 1
                                    logger.info("사용자 {} - {}회차: 음수 금액 실패 - 예외", userId, round + 1)
                                }

                                2 -> {
                                    val zeroAmount = 0L
                                    val isChargeZero = random.nextBoolean()

                                    if (isChargeZero) {
                                        logger.info("사용자 {} - {}회차: 0원 충전 시도 - 예외", userId, round + 1)

                                        mockMvc.perform(
                                            MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(PointAmountRequest(zeroAmount)))
                                        ).andExpect(status().isBadRequest)

                                        logger.info("사용자 {} - {}회차: 0원 충전 실패 - 예외", userId, round + 1)
                                    } else {
                                        logger.info("사용자 {} - {}회차: 0원 사용 시도 - 예외", userId, round + 1)

                                        mockMvc.perform(
                                            MockMvcRequestBuilders.patch("/point/{id}/use", userId)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(PointAmountRequest(zeroAmount)))
                                        ).andExpect(status().isBadRequest)

                                        logger.info("사용자 {} - {}회차: 0원 사용 실패 - 예외", userId, round + 1)
                                    }

                                    userStats[userId]!!["failed"] = userStats[userId]!!["failed"]!! + 1
                                }

                                3 -> {
                                    val excessiveCharge = 1_500_000L
                                    logger.info(
                                        "사용자 {} - {}회차: 최대 금액 초과 충전 시도 {}원 - 예외",
                                        userId, round + 1, excessiveCharge
                                    )

                                    try {
                                        val request = PointAmountRequest(excessiveCharge)

                                        mockMvc.perform(
                                            MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request))
                                        ).andExpect(status().isBadRequest)

                                        userStats[userId]!!["failed"] = userStats[userId]!!["failed"]!! + 1
                                        logger.info("사용자 {} - {}회차: 최대 금액 초과 실패 - 예외", userId, round + 1)

                                    } catch (e: IllegalArgumentException) {
                                        userStats[userId]!!["failed"] = userStats[userId]!!["failed"]!! + 1
                                        logger.info("사용자 {} - {}회차: 최대 금액 초과 실패 - 예외", userId, round + 1)
                                    }
                                }
                            }
                        }
                    }

                } catch (e: Exception) {
                    userStats[userId]!!["exception"] = userStats[userId]!!["exception"]!! + 1
                    logger.error("사용자 {} - {}회차: 예상치 못한 예외 - {}", userId, round + 1, e.message)
                }
            }

            logger.info("사용자 {} 완료", userId)
        }

        logger.info("통계")
        userIds.forEach { userId ->
            val stats = userStats[userId]!!
            logger.info(
                "사용자 {}: 성공={}회, 실패={}회, 예외={}회, 충전={}회, 사용={}회",
                userId, stats["success"], stats["failed"], stats["exception"], stats["charge"], stats["use"]
            )
        }

        logger.info("검증")
        userIds.forEach { userId ->
            val finalPoint = userPointTable.selectById(userId)
            val histories = pointHistoryTable.selectAllByUserId(userId)

            logger.info("사용자 {} 결과:", userId)
            logger.info("  - 최종 포인트: {}원", finalPoint.point)
            logger.info("  - 총 횟수: {}회", histories.size)

            var totalCharge = 0L
            var totalUse = 0L
            var chargeCount = 0
            var useCount = 0

            histories.forEach { history ->
                when (history.type) {
                    TransactionType.CHARGE -> {
                        totalCharge += history.amount
                        chargeCount++
                    }

                    TransactionType.USE -> {
                        totalUse += history.amount
                        useCount++
                    }
                }
            }

            logger.info("  - 총 충전: {}원 ({}회)", totalCharge, chargeCount)
            logger.info("  - 총 사용: {}원 ({}회)", totalUse, useCount)
            logger.info(
                "  - 계산 검증: {} - {} = {} (실제: {})",
                totalCharge,
                totalUse,
                totalCharge - totalUse,
                finalPoint.point
            )

            assert(finalPoint.point == totalCharge - totalUse) {
                "사용자 $userId 포인트 계산 오류: 예상=${totalCharge - totalUse}, 실제=${finalPoint.point}"
            }

            assert(finalPoint.point >= 0) {
                "사용자 $userId 포인트가 음수: ${finalPoint.point}"
            }

            val expectedHistoryCount = userStats[userId]!!["success"]!!
            assert(histories.size == expectedHistoryCount) {
                "사용자 $userId 히스토리 개수 불일치: 예상=$expectedHistoryCount, 실제=${histories.size}"
            }

            logger.info("사용자 {} 검증 완료 ✓", userId)
        }

        val totalSuccess = userStats.values.sumOf { it["success"]!! }
        val totalFailed = userStats.values.sumOf { it["failed"]!! }
        val totalException = userStats.values.sumOf { it["exception"]!! }
        val totalAttempts = totalSuccess + totalFailed + totalException

        logger.info("전체 통계")
        logger.info("총 시도: {}회", totalAttempts)
        logger.info("성공: {}회 ({}%)", totalSuccess, String.format("%.1f", totalSuccess * 100.0 / totalAttempts))
        logger.info("실패: {}회 ({}%)", totalFailed, String.format("%.1f", totalFailed * 100.0 / totalAttempts))
    }
}
