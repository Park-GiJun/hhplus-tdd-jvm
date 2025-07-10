package io.hhplus.tdd

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.hhplus.tdd.dto.PointAmountRequest
import io.hhplus.tdd.dto.PointRequest
import io.hhplus.tdd.dto.PointUpdateResponse
import io.hhplus.tdd.point.PointController
import io.hhplus.tdd.point.TransactionType
import io.hhplus.tdd.point.UserPoint
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
        @DisplayName("0 사용자 ID로 요청시 400 에러")
        fun getPoint_ZeroUserId_ReturnsBadRequest() {
            mockMvc.perform(MockMvcRequestBuilders.get("/point/{id}", 0))
                .andExpect(status().isBadRequest)
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
    }
}