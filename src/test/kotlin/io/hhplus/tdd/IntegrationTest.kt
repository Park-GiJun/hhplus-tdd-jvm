package io.hhplus.tdd

import com.fasterxml.jackson.databind.ObjectMapper
import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import io.hhplus.tdd.dto.PointAmountRequest
import io.hhplus.tdd.point.TransactionType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        clear()
    }

    private fun clear() {
        val userTableField = UserPointTable::class.java.getDeclaredField("table")
        userTableField.isAccessible = true
        val userTable = userTableField.get(userPointTable) as HashMap<*, *>
        userTable.clear()

        val historyTableField = PointHistoryTable::class.java.getDeclaredField("table")
        historyTableField.isAccessible = true
        val historyTable = historyTableField.get(pointHistoryTable) as MutableList<*>
        historyTable.clear()

        val cursorField = PointHistoryTable::class.java.getDeclaredField("cursor")
        cursorField.isAccessible = true
        cursorField.set(pointHistoryTable, 1L)
    }

    @Test
    @DisplayName("없는 사용자 포인트 조회")
    fun getPoint_InvalidUser() {
        val userId = 1L

        mockMvc.perform(MockMvcRequestBuilders.get("/point/{id}", userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.point").value(0))

        val userPoint = userPointTable.selectById(userId)
        assert(userPoint.point == 0L)
    }

    @Test
    @DisplayName("포인트 충전")
    fun chargePoint_Success() {
        val userId = 1L
        val chargeAmount = 1000L

        val request = PointAmountRequest(chargeAmount)

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.beforePointUpdate").value(0L))
            .andExpect(jsonPath("$.afterPointUpdate").value(chargeAmount))

        val updatedPoint = userPointTable.selectById(userId)
        assert(updatedPoint.point == chargeAmount)

        val histories = pointHistoryTable.selectAllByUserId(userId)
        assert(histories.size == 1)
        assert(histories[0].amount == chargeAmount)
        assert(histories[0].type == TransactionType.CHARGE)
    }

    @Test
    @DisplayName("포인트 사용")
    fun usePoint_Success() {
        val userId = 1L
        val initialPoint = 2000L
        val useAmount = 500L

        userPointTable.insertOrUpdate(userId, initialPoint)

        val request = PointAmountRequest(useAmount)

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/point/{id}/use", userId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.beforePointUpdate").value(initialPoint))
            .andExpect(jsonPath("$.afterPointUpdate").value(initialPoint - useAmount))

        val updatedPoint = userPointTable.selectById(userId)
        assert(updatedPoint.point == initialPoint - useAmount)

        val histories = pointHistoryTable.selectAllByUserId(userId)
        assert(histories.size == 1)
        assert(histories[0].type == TransactionType.USE)
    }

    @Test
    @DisplayName("충전하고 사용하는 시나리오")
    fun singleUser_Success() {
        val userId = 1L

        val charge1 = 1000L
        mockMvc.perform(
            MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(charge1)))
        ).andExpect(status().isOk)

        val use1 = 300L
        mockMvc.perform(
            MockMvcRequestBuilders.patch("/point/{id}/use", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(use1)))
        ).andExpect(status().isOk)

        val charge2 = 2000L
        mockMvc.perform(
            MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(charge2)))
        ).andExpect(status().isOk)

        val charge3 = 500L
        mockMvc.perform(
            MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(charge3)))
        ).andExpect(status().isOk)

        mockMvc.perform(MockMvcRequestBuilders.get("/point/{id}", userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.point").value(3200L))

        mockMvc.perform(MockMvcRequestBuilders.get("/point/{id}/histories", userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(4))

        val histories = pointHistoryTable.selectAllByUserId(userId)
        assert(histories[0].type == TransactionType.CHARGE && histories[0].amount == charge1)
        assert(histories[1].type == TransactionType.USE && histories[1].amount == use1)
        assert(histories[2].type == TransactionType.CHARGE && histories[2].amount == charge2)
        assert(histories[3].type == TransactionType.CHARGE && histories[3].amount == charge3)
    }

    @Test
    @DisplayName("여러 사용자 동시 테스트")
    fun multiUser_Test() {
        val userIds = listOf(1L, 2L, 3L, 4L)
        val repeatCount = 5

        userIds.forEach { userId ->
            repeat(repeatCount) { round ->
                try {
                    Thread.sleep((100..500).random().toLong())

                    val currentPoint = userPointTable.selectById(userId).point
                    val random = kotlin.random.Random
                    val isCharge = random.nextBoolean() || (currentPoint == 0L)

                    if (isCharge) {
                        val chargeAmount = (random.nextInt(16) + 5) * 100L
                        mockMvc.perform(
                            MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(PointAmountRequest(chargeAmount)))
                        ).andExpect(status().isOk)
                    } else {
                        if (currentPoint > 0) {
                            val maxUse = (currentPoint * 0.8).toLong()
                            val useAmount = random.nextLong(maxUse) + 100L
                            if (useAmount <= currentPoint) {
                                mockMvc.perform(
                                    MockMvcRequestBuilders.patch("/point/{id}/use", userId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(PointAmountRequest(useAmount)))
                                ).andExpect(status().isOk)
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("사용자 $userId 에러: ${e.message}")
                }
            }
        }

        userIds.forEach { userId ->
            val finalPoint = userPointTable.selectById(userId)
            val histories = pointHistoryTable.selectAllByUserId(userId)

            val totalCharge = histories.filter { it.type == TransactionType.CHARGE }.sumOf { it.amount }
            val totalUse = histories.filter { it.type == TransactionType.USE }.sumOf { it.amount }

            assert(finalPoint.point == totalCharge - totalUse)
            assert(finalPoint.point >= 0)
            assert(histories.isNotEmpty())
        }
    }

    @Test
    @DisplayName("잘못된 요청 테스트")
    fun invalidRequests_Test() {
        val userId = 1L
        userPointTable.insertOrUpdate(userId, 1000L)

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(-100L)))
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(0L)))
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/point/{id}/use", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(2000L)))
        ).andExpect(status().isBadRequest)

        val finalPoint = userPointTable.selectById(userId)
        assert(finalPoint.point == 1000L)
    }

    @Test
    @DisplayName("예외 케이스 랜덤 테스트")
    fun randomExceptionTest() {
        val userIds = listOf(1L, 2L, 3L)
        val repeatCount = 3
        val random = kotlin.random.Random

        var successCount = 0
        var failCount = 0

        userIds.forEach { userId ->
            repeat(repeatCount) {
                try {
                    Thread.sleep(random.nextInt(200).toLong() + 100)

                    val currentPoint = userPointTable.selectById(userId).point
                    val actionType = random.nextInt(10)

                    when {
                        actionType < 3 -> {
                            val negativeAmount = -(random.nextInt(1000) + 100L)
                            try {
                                mockMvc.perform(
                                    MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(PointAmountRequest(negativeAmount)))
                                ).andExpect(status().isBadRequest)
                                failCount++
                            } catch (e: Exception) {
                                failCount++
                            }
                        }
                        actionType < 5 -> {
                            val excessiveAmount = currentPoint + random.nextInt(1000) + 500L
                            try {
                                mockMvc.perform(
                                    MockMvcRequestBuilders.patch("/point/{id}/use", userId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(PointAmountRequest(excessiveAmount)))
                                ).andExpect(status().isBadRequest)
                                failCount++
                            } catch (e: Exception) {
                                failCount++
                            }
                        }
                        actionType < 6 -> {
                            try {
                                mockMvc.perform(
                                    MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(PointAmountRequest(0L)))
                                ).andExpect(status().isBadRequest)
                                failCount++
                            } catch (e: Exception) {
                                failCount++
                            }
                        }
                        else -> {
                            val chargeAmount = (random.nextInt(10) + 1) * 100L
                            mockMvc.perform(
                                MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(PointAmountRequest(chargeAmount)))
                            ).andExpect(status().isOk)
                            successCount++
                        }
                    }
                } catch (e: Exception) {
                    println("예외 발생: ${e.message}")
                }
            }
        }

        userIds.forEach { userId ->
            val finalPoint = userPointTable.selectById(userId)
            val histories = pointHistoryTable.selectAllByUserId(userId)

            val totalCharge = histories.filter { it.type == TransactionType.CHARGE }.sumOf { it.amount }
            val totalUse = histories.filter { it.type == TransactionType.USE }.sumOf { it.amount }

            assert(finalPoint.point == totalCharge - totalUse)
            assert(finalPoint.point >= 0)
        }

        println("성공: $successCount, 실패: $failCount")
    }
}