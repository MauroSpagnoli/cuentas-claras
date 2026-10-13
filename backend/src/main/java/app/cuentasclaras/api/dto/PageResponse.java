package app.cuentasclaras.api.dto;

import app.cuentasclaras.domain.model.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envoltorio de paginacion por cursor usado por la API.
 */
public record PageResponse<T>(
        List<T> items,
        String nextCursor,
        boolean hasMore
) {

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.items().stream().map(mapper).toList(),
                page.nextCursor(),
                page.hasMore()
        );
    }
}
