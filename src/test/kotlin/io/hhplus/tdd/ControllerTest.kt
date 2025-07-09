package io.hhplus.tdd

import com.fasterxml.jackson.databind.ObjectMapper
import io.hhplus.tdd.dto.PointRequest
import io.hhplus.tdd.point.PointController
import io.hhplus.tdd.point.TransactionType
import io.hhplus.tdd.point.UserPoint
import io.hhplus.tdd.service.PointService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.mockito.BDDMockito.given

@WebMvcTest(PointController::class)
@DisplayName("PointController 테스트")
class ControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var pointService: PointService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val logger = LoggerFactory.getLogger(ControllerTest::class.java)

    @Nested
    @DisplayName("사용자 포인트 조회")
    inner class GetUserPointTest {

        @Test
        @DisplayName("정상적인 사용자 ID로 포인트 조회 성공")
        fun getUserPoint_ValidId_Success() {
            val userId = 1L
            val expectedUserPoint = UserPoint(userId, 1000L, System.currentTimeMillis())

            given(pointService.getUserPoint(userId)).willReturn(expectedUserPoint)

            logger.info("조회 ID: {}, 예상 포인트: {}", userId, expectedUserPoint.point)

            mockMvc.perform(get("/point/{id}", userId))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(1000))

            logger.info("포인트 조회 API 테스트 성공")
        }

        @Test
        @DisplayName("존재하지 않는 사용자 ID로 조회 시 예외 발생")
        fun getUserPoint_NonExistentId_ThrowsException() {
            val nonExistentUserId = 999L
            val errorMessage = "사용자를 찾을 수 없습니다."

            given(pointService.getUserPoint(nonExistentUserId))
                .willThrow(IllegalArgumentException(errorMessage))

            logger.info("조회 ID: {}", nonExistentUserId)

            mockMvc.perform(get("/point/{id}", nonExistentUserId))
                .andExpect(status().isBadRequest)

            logger.info("존재하지 않는 사용자 조회 테스트 성공")
        }

        @Test
        @DisplayName("음수 사용자 ID로 조회 시 예외 발생")
        fun getUserPoint_NegativeId_ThrowsException() {
            val negativeUserId = -1L
            val errorMessage = "유저 아이디는 0보다 작을수 없습니다."

            given(pointService.getUserPoint(negativeUserId))
                .willThrow(IllegalArgumentException(errorMessage))

            logger.info("조회 ID: {}", negativeUserId)

            mockMvc.perform(get("/point/{id}", negativeUserId))
                .andExpect(status().isBadRequest)

            logger.info("음수 사용자 ID 조회 테스트 성공")
        }

        @Test
        @DisplayName("0인 사용자 ID로 조회 시 예외 발생")
        fun getUserPoint_ZeroId_ThrowsException() {
            val zeroUserId = 0L
            val errorMessage = "유저 아이디는 0보다 작을수 없습니다."

            given(pointService.getUserPoint(zeroUserId))
                .willThrow(IllegalArgumentException(errorMessage))

            logger.info("조회 ID: {}", zeroUserId)

            mockMvc.perform(get("/point/{id}", zeroUserId))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value(errorMessage))

            logger.info("0인 사용자 ID 조회 테스트 성공")
        }
    }

    @Nested
    @DisplayName("사용자 포인트 충전")
    inner class ChargePointTest {
    }
}