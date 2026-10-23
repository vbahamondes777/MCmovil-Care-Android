package cl.mcmovil.care.model

/** Representación de un equipo en el MVP. */
data class Device(
    val name: String,
    val serial: String,
    val status: String,
    val warranty: String,
    val history: String,
    val maintenance: String
)
