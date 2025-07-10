package io.hhplus.tdd.point

data class UserPoint(
    val id: Long,
    val point: Long,
    val updateMillis: Long,
) {
    companion object {
        const val MAX_POINT = 100_000_000L
    }


    fun charge(amount: Long): UserPoint {
        val newPoint = this.point + amount
        validateMaxPoint(newPoint)

        return this.copy(
            point = newPoint,
            updateMillis = System.currentTimeMillis(),
        )
    }

    fun use(amount: Long): UserPoint {
        validateSufficientBalance(amount)

        val newPoint = this.point - amount

        return this.copy(
            point = newPoint,
            updateMillis = System.currentTimeMillis(),
        )
    }

    private fun validateSufficientBalance(amount: Long) {
        require(this.point >= amount) {
            "잔액이 부족합니다. 현재 포인트: ${this.point}원, 사용 요청: ${amount}원"
        }
    }

    private fun validateMaxPoint(point: Long) {
        require(point <= MAX_POINT) {
            "포인트가 최대값을 초과할 수 없습니다. 최대값: ${MAX_POINT}원, 요청값: ${point}원"
        }
    }
}
