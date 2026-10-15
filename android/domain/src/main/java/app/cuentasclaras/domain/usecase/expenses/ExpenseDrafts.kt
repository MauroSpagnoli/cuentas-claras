package app.cuentasclaras.domain.usecase.expenses

import app.cuentasclaras.domain.model.ExpenseDraft

/**
 * Normaliza los textos del formulario antes de enviarlos: recorta espacios y
 * convierte en `null` la categoría y las notas vacías.
 */
internal fun ExpenseDraft.normalized(): ExpenseDraft = copy(
    description = description.trim(),
    category = category?.trim()?.takeIf { it.isNotEmpty() },
    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
)
