package io.hhplus.tdd.dto

data class CommonResponse<T>(
    val result: Boolean,
    val data: T? = null,
    val message: String? = null
) {
    companion object {
        fun <T> success(data: T, message: String? = null): CommonResponse<T> {
            return CommonResponse(result = true, data = data, message = message)
        }

        fun <T> success(message: String): CommonResponse<T> {
            return CommonResponse(result = true, data = null, message = message)
        }

        fun <T> error(message: String): CommonResponse<T> {
            return CommonResponse(result = false, data = null, message = message)
        }
    }
}
