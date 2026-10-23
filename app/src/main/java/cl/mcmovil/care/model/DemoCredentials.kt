package cl.mcmovil.care.model

/** Usuarios ficticios para demostrar el acceso diferenciado por rol. */
object DemoCredentials {
    fun forRole(role: String): Pair<String, String> = when (role) {
        "Cliente" -> "cliente" to "Cliente123"
        "Técnico" -> "tecnico" to "Tecnico123"
        else -> "admin" to "Admin123"
    }
}
