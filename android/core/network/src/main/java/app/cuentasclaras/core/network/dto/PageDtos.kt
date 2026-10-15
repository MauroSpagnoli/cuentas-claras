package app.cuentasclaras.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Página de resultados del backend basada en cursor opaco.
 * `nextCursor` es nulo cuando ya no hay más páginas.
 */
@Serializable
data class PageDto<T>(
    val items: List<T> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)
