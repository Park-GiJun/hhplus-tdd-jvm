package io.hhplus.tdd.point

import io.hhplus.tdd.dto.PointAmountRequest
import io.hhplus.tdd.dto.PointRequest
import io.hhplus.tdd.dto.PointUpdateResponse
import io.hhplus.tdd.service.PointService
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/point")
class PointController(
    private val pointService: PointService
) {
    /**
     * TODO - 특정 유저의 포인트를 조회하는 기능을 작성해주세요.
     */
    @GetMapping("{id}")
    fun point(
        @PathVariable @Positive id: Long,
    ): ResponseEntity<UserPoint> {
        return ResponseEntity.ok(pointService.getUserPoint(id))
    }

    /**
     * TODO - 특정 유저의 포인트 충전/이용 내역을 조회하는 기능을 작성해주세요.
     */
    @GetMapping("{id}/histories")
    fun history(
        @PathVariable @Positive id: Long,
    ): ResponseEntity<List<PointHistory>> {
        return ResponseEntity.ok(pointService.selectAllByUserId(id))
    }

    /**
     * TODO - 특정 유저의 포인트를 충전하는 기능을 작성해주세요.
     */
    @PatchMapping("{id}/charge")
    fun charge(
        @PathVariable @Positive id: Long,
        @RequestBody @Valid request: PointAmountRequest,
    ): ResponseEntity<PointUpdateResponse> {
        val pointRequest = PointRequest(id, request.amount, TransactionType.CHARGE)
        return ResponseEntity.ok(pointService.updateUserPoint(pointRequest))
    }

    /**
     * TODO - 특정 유저의 포인트를 사용하는 기능을 작성해주세요.
     */
    @PatchMapping("{id}/use")
    fun use(
        @PathVariable @Positive id: Long,
        @RequestBody @Valid request: PointAmountRequest,
    ): ResponseEntity<PointUpdateResponse> {
        val pointRequest = PointRequest(id, request.amount, TransactionType.USE)
        return ResponseEntity.ok(pointService.updateUserPoint(pointRequest))
    }
}