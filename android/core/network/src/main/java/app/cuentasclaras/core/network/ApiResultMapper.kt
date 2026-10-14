package app.cuentasclaras.core.network

import app.cuentasclaras.core.network.dto.ProblemDto
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/**
 * Ejecuta una llamada Retrofit y traduce el resultado a [AppResult], mapeando
 * los errores RFC 7807 a [AppError].
 */
suspend fun <T : Any> safeApiCall(
    json: Json,
    block: suspend () -> Response<T>,
): AppResult<T> = try {
    val response = block()
    if (response.isSuccessful) {
        val body = response.body()
        if (body != null) {
            AppResult.Success(body)
        } else {
            AppResult.Failure(AppError.Unexpected("Respuesta vacía del servidor"))
        }
    } else {
        AppResult.Failure(response.toAppError(json))
    }
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (io: IOException) {
    AppResult.Failure(AppError.Network)
} catch (t: Throwable) {
    AppResult.Failure(AppError.Unexpected(t.message))
}

fun Response<*>.toAppError(json: Json): AppError {
    val problem = errorBody()?.string()?.let { raw ->
        runCatching { json.decodeFromString(ProblemDto.serializer(), raw) }.getOrNull()
    }
    val message = problem?.detail ?: problem?.title
    return when (code()) {
        401 -> AppError.Unauthorized
        403 -> AppError.Forbidden
        404 -> AppError.NotFound
        409 -> AppError.Conflict(message)
        400, 422 -> AppError.Validation(message, problem.toFieldErrors())
        in 500..599 -> AppError.Server(code(), message)
        else -> AppError.Unexpected(message ?: "HTTP ${code()}")
    }
}

private fun ProblemDto?.toFieldErrors(): Map<String, String> =
    this?.errors
        ?.mapNotNull { error ->
            val field = error.field ?: return@mapNotNull null
            val message = error.message ?: return@mapNotNull null
            field to message
        }
        ?.toMap()
        .orEmpty()
