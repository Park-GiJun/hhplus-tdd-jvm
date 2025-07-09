package io.hhplus.tdd.dto

data class ChargeRequest(
    val amount: Long
) {
    init {
        require(amount > 0) { "충전 금액은 양수여야 합니다" }
        require(amount <= 1000000) { "최대 충전 금액을 초과했습니다" }
    }
}