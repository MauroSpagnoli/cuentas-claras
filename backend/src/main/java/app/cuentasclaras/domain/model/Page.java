package app.cuentasclaras.domain.model;

import java.util.List;

/**
 * Pagina por cursor: elementos + cursor siguiente + indicador de si hay mas.
 */
public record Page<T>(List<T> items, String nextCursor, boolean hasMore) {
}
