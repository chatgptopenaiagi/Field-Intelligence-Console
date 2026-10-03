package com.fieldconsole.data.emergency

/**
 * EmergencyNumbers — country-aware emergency contact database.
 *
 * Provides a static lookup by ISO 3166-1 alpha-2 country code.
 * Falls back to EU/international numbers when the country is unknown.
 */
data class EmergencyNumber(
    val label:       String,
    val number:      String,
    val description: String,
    val category:    Category
) {
    enum class Category { POLICE, FIRE, AMBULANCE, MEDICAL_ADVICE, PHARMACY, GAS, POISON, GENERAL }
}

object EmergencyNumbers {

    fun forCountry(isoCode: String): List<EmergencyNumber> =
        COUNTRY_MAP[isoCode.uppercase()] ?: INTERNATIONAL

    // ── Country databases ─────────────────────────────────────────────────────

    private val INTERNATIONAL = listOf(
        EmergencyNumber("European Emergency", "112", "Universal EU emergency number", EmergencyNumber.Category.GENERAL),
        EmergencyNumber("International SOS",  "+1-215-942-8226", "International assistance", EmergencyNumber.Category.GENERAL)
    )

    private val AUSTRIA = listOf(
        EmergencyNumber("European Emergency",  "112",  "Universal EU emergency",        EmergencyNumber.Category.GENERAL),
        EmergencyNumber("Police",              "133",  "Polizei",                       EmergencyNumber.Category.POLICE),
        EmergencyNumber("Fire Brigade",        "122",  "Feuerwehr",                     EmergencyNumber.Category.FIRE),
        EmergencyNumber("Ambulance",           "144",  "Rettung / Notarzt",             EmergencyNumber.Category.AMBULANCE),
        EmergencyNumber("Medical Advice",      "1450", "Ärztefunkdienst",               EmergencyNumber.Category.MEDICAL_ADVICE),
        EmergencyNumber("Pharmacy Hotline",    "1455", "Apothekenbereitschaft",          EmergencyNumber.Category.PHARMACY),
        EmergencyNumber("Gas Emergency",       "128",  "Gasnetz Wien / regional",       EmergencyNumber.Category.GAS),
        EmergencyNumber("Poison Centre",       "+43-1-406-43-43", "Vergiftungsinformationszentrale", EmergencyNumber.Category.POISON),
        EmergencyNumber("Mountain Rescue",     "140",  "Bergrettung",                   EmergencyNumber.Category.GENERAL),
        EmergencyNumber("ÖAMTC Breakdown",     "120",  "Pannenhilfe",                   EmergencyNumber.Category.GENERAL)
    )

    private val GERMANY = listOf(
        EmergencyNumber("European Emergency",  "112",  "Europäischer Notruf",           EmergencyNumber.Category.GENERAL),
        EmergencyNumber("Police",              "110",  "Polizei",                       EmergencyNumber.Category.POLICE),
        EmergencyNumber("Fire / Ambulance",    "112",  "Feuerwehr / Rettungsdienst",    EmergencyNumber.Category.FIRE),
        EmergencyNumber("Medical Advice",      "116117","Ärztlicher Bereitschaftsdienst",EmergencyNumber.Category.MEDICAL_ADVICE),
        EmergencyNumber("Poison Centre",       "+49-30-19240", "Giftnotruf Berlin",     EmergencyNumber.Category.POISON),
        EmergencyNumber("ADAC Breakdown",      "22222","Pannenhilfe",                   EmergencyNumber.Category.GENERAL)
    )

    private val UK = listOf(
        EmergencyNumber("Emergency Services",  "999",  "Police, Fire, Ambulance",       EmergencyNumber.Category.GENERAL),
        EmergencyNumber("European Emergency",  "112",  "EU standard",                   EmergencyNumber.Category.GENERAL),
        EmergencyNumber("NHS Non-Emergency",   "111",  "Medical advice",                EmergencyNumber.Category.MEDICAL_ADVICE),
        EmergencyNumber("Police Non-Emergency","101",  "Non-urgent police",             EmergencyNumber.Category.POLICE),
        EmergencyNumber("Poison Centre",       "+44-344-892-0111","National Poisons Information", EmergencyNumber.Category.POISON)
    )

    private val USA = listOf(
        EmergencyNumber("Emergency Services",  "911",  "Police, Fire, Ambulance",       EmergencyNumber.Category.GENERAL),
        EmergencyNumber("Poison Control",      "1-800-222-1222", "ASPCA Poison Control", EmergencyNumber.Category.POISON),
        EmergencyNumber("Crisis Line",         "988",  "Suicide & Crisis Lifeline",     EmergencyNumber.Category.MEDICAL_ADVICE)
    )

    private val SWITZERLAND = listOf(
        EmergencyNumber("European Emergency",  "112",  "EU standard",                   EmergencyNumber.Category.GENERAL),
        EmergencyNumber("Police",              "117",  "Polizei",                       EmergencyNumber.Category.POLICE),
        EmergencyNumber("Fire Brigade",        "118",  "Feuerwehr",                     EmergencyNumber.Category.FIRE),
        EmergencyNumber("Ambulance",           "144",  "Sanität",                       EmergencyNumber.Category.AMBULANCE),
        EmergencyNumber("Medical Advice",      "1811", "Medizinische Auskunft",         EmergencyNumber.Category.MEDICAL_ADVICE),
        EmergencyNumber("Poison Centre",       "145",  "Tox Info Suisse",               EmergencyNumber.Category.POISON),
        EmergencyNumber("REGA Air Rescue",     "1414", "Rega Helikopter",               EmergencyNumber.Category.AMBULANCE)
    )

    private val FRANCE = listOf(
        EmergencyNumber("European Emergency",  "112",  "Numéro d'urgence européen",     EmergencyNumber.Category.GENERAL),
        EmergencyNumber("Police",              "17",   "Police Nationale",              EmergencyNumber.Category.POLICE),
        EmergencyNumber("Fire Brigade",        "18",   "Pompiers",                      EmergencyNumber.Category.FIRE),
        EmergencyNumber("SAMU Ambulance",      "15",   "Service d'Aide Médicale Urgente",EmergencyNumber.Category.AMBULANCE),
        EmergencyNumber("Poison Centre",       "+33-1-40-05-48-48","Centre antipoison Paris", EmergencyNumber.Category.POISON)
    )

    private val ITALY = listOf(
        EmergencyNumber("European Emergency",  "112",  "Numero di emergenza europeo",   EmergencyNumber.Category.GENERAL),
        EmergencyNumber("Police",              "113",  "Polizia di Stato",              EmergencyNumber.Category.POLICE),
        EmergencyNumber("Carabinieri",         "112",  "Arma dei Carabinieri",          EmergencyNumber.Category.POLICE),
        EmergencyNumber("Fire Brigade",        "115",  "Vigili del Fuoco",              EmergencyNumber.Category.FIRE),
        EmergencyNumber("Ambulance",           "118",  "Emergenza Sanitaria",           EmergencyNumber.Category.AMBULANCE),
        EmergencyNumber("Poison Centre",       "+39-06-3054343","Centro Antiveleni Roma", EmergencyNumber.Category.POISON)
    )

    private val COUNTRY_MAP: Map<String, List<EmergencyNumber>> = mapOf(
        "AT" to AUSTRIA,
        "DE" to GERMANY,
        "GB" to UK,
        "US" to USA,
        "CH" to SWITZERLAND,
        "FR" to FRANCE,
        "IT" to ITALY
    )
}
