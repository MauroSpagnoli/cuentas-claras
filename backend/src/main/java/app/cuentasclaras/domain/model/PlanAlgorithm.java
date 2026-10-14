package app.cuentasclaras.domain.model;

/**
 * Algoritmo con el que se construyo un plan de liquidacion.
 */
public enum PlanAlgorithm {

    /** Minimo global (subset-sum exacto); solo para grupos pequenos. */
    EXACT,

    /** Heuristica voraz (mayor deudor paga al mayor acreedor). */
    GREEDY
}
