package app.cuentasclaras.domain.model

/**
 * Mutación de un recurso pendiente de sincronizar. Se usa como estado optimista
 * en los modelos de dominio y como valor guardado en el outbox local.
 */
enum class OutboxOperation {
    CREATE,
    UPDATE,
    DELETE,
    ;

    companion object {
        /** Convierte el valor almacenado; devuelve `null` si no lo reconoce. */
        fun fromStorage(raw: String?): OutboxOperation? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}
