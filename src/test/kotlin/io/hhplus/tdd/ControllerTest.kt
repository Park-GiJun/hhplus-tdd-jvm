package io.hhplus.tdd

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.hhplus.tdd.dto.PointAmountRequest
import io.hhplus.tdd.dto.PointRequest
import io.hhplus.tdd.dto.PointUpdateResponse
import io.hhplus.tdd.point.PointController
import io.hhplus.tdd.point.TransactionType
import io.hhplus.tdd.point.UserPoint
import io.hhplus.tdd.point.UserPoint.Companion.MAX_POINT
import io.hhplus.tdd.service.PointService
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(PointController::class)
@DisplayName("PointController 테스트")
class ControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var pointService: PointService

    @Nested
    @DisplayName("GET /point/{id} - 포인트 조회")
    inner class GetUserPoint {

        @Test
        @DisplayName("정상적인 포인트 조회")
        fun getPoint_ValidUserId_ReturnsUserPoint() {
            val userId = 1L
            val userPoint = UserPoint(userId, 1000L, System.currentTimeMillis())
            every { pointService.getUserPoint(userId) } returns userPoint

            mockMvc.perform(MockMvcRequestBuilders.get("/point/{id}", userId))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(1000))
                .andExpect(jsonPath("$.updateMillis").exists())

            verify { pointService.getUserPoint(userId) }
        }

        @Test
        @DisplayName("음수 사용자 ID로 요청시 400 에러")
        fun getPoint_NegativeUserId_ReturnsBadRequest() {
            mockMvc.perform(MockMvcRequestBuilders.get("/point/{id}", -1))
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("0 사용자 ID로 요청시 기본 포인트 반환")
        fun getPoint_ZeroUserId_ReturnsDefaultPoint() {
            val userId = 0L
            val userPoint = UserPoint(userId, 0L, System.currentTimeMillis())
            every { pointService.getUserPoint(userId) } returns userPoint

            mockMvc.perform(MockMvcRequestBuilders.get("/point/{id}", userId))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(0))
                .andExpect(jsonPath("$.point").value(0))

            verify { pointService.getUserPoint(userId) }
        }
    }

    @Nested
    @DisplayName("PATCH /point/{id}/charge - 포인트 충전")
    inner class ChargePoint {

        @Test
        @DisplayName("정상적인 포인트 충전")
        fun chargePoint_ValidPoint_ReturnsPoint() {
            val userId = 1L
            val chargeAmount = 1000L
            val request = PointAmountRequest(chargeAmount)
            val pointRequest = PointRequest(userId, chargeAmount, TransactionType.CHARGE)
            val response = PointUpdateResponse(
                userId = userId,
                beforePointUpdate = 1000L,
                afterPointUpdate = 2000L,
                updatedMillis = System.currentTimeMillis()
            )

            every { pointService.updateUserPoint(pointRequest) } returns response

            mockMvc.perform(
                MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.beforePointUpdate").value(1000L))
                .andExpect(jsonPath("$.afterPointUpdate").value(2000L))
                .andExpect(jsonPath("$.updatedMillis").exists())

            verify { pointService.updateUserPoint(pointRequest) }
        }

        @Test
        @DisplayName("음수 금액으로 충전시 400 에러")
        fun chargePoint_NegativeAmount_ReturnsBadRequest() {
            val userId = 1L
            val request = PointAmountRequest(-1000L)

            mockMvc.perform(
                MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("0원 충전시 400 에러")
        fun chargePoint_ZeroAmount_ReturnsBadRequest() {
            val userId = 1L
            val request = PointAmountRequest(0L)

            mockMvc.perform(
                MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("음수 사용자 ID로 충전시 400 에러")
        fun chargePoint_NegativeUserId_ReturnsBadRequest() {
            val request = PointAmountRequest(1000L)

            mockMvc.perform(
                MockMvcRequestBuilders.patch("/point/{id}/charge", -1)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("현재 포인트가 99,999,000원일 때 2,000원 충전시 최대값 초과 에러")
        fun chargePoint_WouldExceedMaxPoint_ReturnsBadRequest() {
            val userId = 1L
            val chargeAmount = 2000L
            val request = PointAmountRequest(chargeAmount)
            val pointRequest = PointRequest(userId, chargeAmount, TransactionType.CHARGE)

            every { pointService.updateUserPoint(pointRequest) } throws
                    IllegalArgumentException("포인트가 최대값을 초과할 수 없습니다. 최대값: ${MAX_POINT}원, 요청값: ${100_001_000L}원")

            mockMvc.perform(
                MockMvcRequestBuilders.patch("/point/{id}/charge", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("포인트가 최대값을 초과할 수 없습니다. 최대값: ${MAX_POINT}원, 요청값: ${100_001_000L}원"))

            verify(exactly = 0) { pointService.updateUserPoint(pointRequest) }
        }
    }

    @Nested
    @DisplayName("PATCH /point/{id}/use - 포인트 사용")
    inner class UsePoint {

        @Test
        @DisplayName("정상적인 포인트 사용")
        fun usePoint_ValidPoint_ReturnsPoint() {
            val userId = 1L
            val useAmount = 500L
            val request = PointAmountRequest(useAmount)
            val pointRequest = PointRequest(userId, useAmount, TransactionType.USE)
            val response = PointUpdateResponse(
                userId = userId,
                beforePointUpdate = 1000L,
                afterPointUpdate = 500L,
                updatedMillis = System.currentTimeMillis()
            )

            every { pointService.updateUserPoint(pointRequest) } returns response

            mockMvc.perform(
                MockMvcRequestBuilders.patch("/point/{id}/use", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.beforePointUpdate").value(1000L))
                .andExpect(jsonPath("$.afterPointUpdate").value(500L))
                .andExpect(jsonPath("$.updatedMillis").exists())

            verify { pointService.updateUserPoint(pointRequest) }
        }

        @Test
        @DisplayName("음수 금액으로 사용시 400 에러")
        fun usePoint_NegativeAmount_ReturnsBadRequest() {
            val userId = 1L
            val request = PointAmountRequest(-500L)

            mockMvc.perform(
                MockMvcRequestBuilders.patch("/point/{id}/use", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("0원 사용시 400 에러")
        fun usePoint_ZeroAmount_ReturnsBadRequest() {
            val userId = 1L
            val request = PointAmountRequest(0L)

            mockMvc.perform(
                MockMvcRequestBuilders.patch("/point/{id}/use", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("음수 사용자 ID로 사용시 400 에러")
        fun usePoint_NegativeUserId_ReturnsBadRequest() {
            val request = PointAmountRequest(500L)

            mockMvc.perform(
                MockMvcRequestBuilders.patch("/point/{id}/use", -1)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("0 사용자 ID로 사용시 400 에러")
        fun usePoint_ZeroUserId_ReturnsBadRequest() {
            val request = PointAmountRequest(500L)

            mockMvc.perform(
                MockMvcRequestBuilders.patch("/point/{id}/use", 0)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("보유 포인트보다 많은 포인트 사용시 400 에러")
        fun usePoint_InsufficientPoints_ReturnsBadRequest() {
            val userId = 1L
            val useAmount = 2000L
            val request = PointAmountRequest(useAmount)
            val pointRequest = PointRequest(userId, useAmount, TransactionType.USE)

            every { pointService.updateUserPoint(pointRequest) } throws
                    IllegalArgumentException("포인트가 부족합니다. 보유: 1000원, 사용 요청: ${useAmount}원")

            mockMvc.perform(
                MockMvcRequestBuilders.patch("/point/{id}/use", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("포인트가 부족합니다. 보유: 1000원, 사용 요청: ${useAmount}원"))

            verify { pointService.updateUserPoint(pointRequest) }
        }

        @Test
        @DisplayName("대용량 포인트 사용시 정상 처리")
        fun usePoint_LargeAmount_ReturnsPoint() {
            val userId = 1L
            val useAmount = 500_000L
            val request = PointAmountRequest(useAmount)
            val pointRequest = PointRequest(userId, useAmount, TransactionType.USE)
            val response = PointUpdateResponse(
                userId = userId,
                beforePointUpdate = 100_000_000L,
                afterPointUpdate = 50_000_000L,
                updatedMillis = System.currentTimeMillis()
            )

            every { pointService.updateUserPoint(pointRequest) } returns response

            mockMvc.perform(
                MockMvcRequestBuilders.patch("/point/{id}/use", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.beforePointUpdate").value(100_000_000L))
                .andExpect(jsonPath("$.afterPointUpdate").value(50_000_000L))
                .andExpect(jsonPath("$.updatedMillis").exists())

            verify { pointService.updateUserPoint(pointRequest) }
        }
    }
}