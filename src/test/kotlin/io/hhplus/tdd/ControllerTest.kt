package io.hhplus.tdd

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.hhplus.tdd.point.PointController
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
}