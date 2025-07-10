package io.hhplus.tdd.dto

import jakarta.validation.constraints.Positive

data class PointAmountRequest(
    @field:Positive(message = "금액은 양수여야 합니다")
    val amount: Long
) {
    init {
        require(amount <= 1000000) { "최대 금액(1,000,000원)을 초과했습니다" }
    }
}
