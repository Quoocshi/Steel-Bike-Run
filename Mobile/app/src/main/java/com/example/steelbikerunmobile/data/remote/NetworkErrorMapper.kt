package com.example.steelbikerunmobile.data.remote

import com.example.steelbikerunmobile.data.remote.dto.ApiEnvelope
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Centralised translation of low-level network/HTTP exceptions into messages a non-technical user
 * can understand. Two responsibilities:
 *
 *  1. Pull the real error message out of the backend's `ApiResponse` envelope when present
 *     (instead of leaking raw "HTTP 409" strings to the UI).
 *  2. Map common transport failures (no internet, server down, timeout) to friendly Vietnamese.
 */
object NetworkErrorMapper {

    private val gson = Gson()
    private val envelopeType = object : TypeToken<ApiEnvelope<Any?>>() {}.type

    /**
     * Wrap a suspend network call so its result is a [Result] whose failure carries a friendly,
     * user-facing message. CancellationException is re-thrown so coroutine cancellation still
     * works correctly.
     *
     * NOTE: deliberately NOT `inline` — combining `inline suspend` with a try/catch around the
     * inlined block has historically produced miscompiled state-machines on some Kotlin
     * versions (the catch frames can be skipped by the suspend continuation), which manifests
     * as silent app crashes. A regular suspend function is the safer choice.
     */
    suspend fun <T> safeCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (httpEx: HttpException) {
        Result.failure(IllegalStateException(extractHttpMessage(httpEx), httpEx))
    } catch (io: IOException) {
        Result.failure(IllegalStateException(extractIoMessage(io), io))
    } catch (t: Throwable) {
        // Last-resort: never let the app crash because of an unexpected exception class
        // bubbling out of a repository. Surface the original message if any.
        Result.failure(IllegalStateException(t.message ?: "Có lỗi xảy ra. Vui lòng thử lại.", t))
    }

    fun extractHttpMessage(httpEx: HttpException): String {
        val rawBody = runCatching { httpEx.response()?.errorBody()?.string() }.getOrNull()
        val parsed = parseEnvelope(rawBody)
        val backendMessage = parsed?.message?.takeIf { it.isNotBlank() }
        if (!backendMessage.isNullOrBlank()) {
            return translateBackendMessage(backendMessage)
        }
        return friendlyForCode(httpEx.code())
    }

    fun extractIoMessage(io: IOException): String = when (io) {
        is UnknownHostException -> "Không tìm thấy máy chủ. Kiểm tra địa chỉ và mạng Wi-Fi."
        is ConnectException -> "Không thể kết nối tới máy chủ. Đảm bảo Backend đang chạy."
        is SocketTimeoutException -> "Máy chủ phản hồi quá lâu. Vui lòng thử lại."
        else -> "Không có kết nối mạng. Kiểm tra Wi-Fi/4G và thử lại."
    }

    private fun parseEnvelope(rawBody: String?): ApiEnvelope<Any?>? {
        if (rawBody.isNullOrBlank()) return null
        return try {
            gson.fromJson<ApiEnvelope<Any?>>(rawBody, envelopeType)
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    private fun friendlyForCode(code: Int): String = when (code) {
        400 -> "Dữ liệu gửi lên không hợp lệ."
        401 -> "Sai tài khoản hoặc mật khẩu."
        403 -> "Tài khoản không có quyền thực hiện hành động này."
        404 -> "Không tìm thấy tài nguyên yêu cầu."
        409 -> "Dữ liệu đã tồn tại trong hệ thống."
        500 -> "Máy chủ đang gặp sự cố. Vui lòng thử lại sau."
        in 502..504 -> "Máy chủ tạm thời không phản hồi. Vui lòng thử lại."
        else -> "Lỗi mạng (mã $code). Vui lòng thử lại."
    }

    /**
     * Translate the most common backend English messages into Vietnamese. Anything we don't
     * recognise is returned verbatim — the backend is increasingly Vietnamese itself.
     */
    private fun translateBackendMessage(raw: String): String = when (raw.trim()) {
        "Email already exists" -> "Email này đã được sử dụng. Vui lòng dùng email khác."
        "Phone number already exists" -> "Số điện thoại này đã được sử dụng."
        "Invalid email/phone or password" -> "Sai email/số điện thoại hoặc mật khẩu."
        "User not found" -> "Tài khoản không tồn tại."
        "Unauthorized" -> "Bạn cần đăng nhập lại."
        "Token is invalid or expired" -> "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        "Access denied" -> "Tài khoản không có quyền thực hiện hành động này."
        "Bad request" -> "Yêu cầu không hợp lệ."
        "Internal server error" -> "Máy chủ đang gặp sự cố. Vui lòng thử lại sau."
        else -> raw
    }
}
