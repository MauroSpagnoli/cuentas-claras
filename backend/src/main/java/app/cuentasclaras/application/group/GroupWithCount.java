package app.cuentasclaras.application.group;

import app.cuentasclaras.domain.model.Group;

/**
 * Grupo con el numero de miembros activos, usado por la API.
 */
public record GroupWithCount(Group group, long memberCount) {
}
