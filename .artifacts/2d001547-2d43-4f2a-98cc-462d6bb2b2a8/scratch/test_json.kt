import kotlinx.serialization.json.*

fun main() {
    val s = "null"
    try {
        val json = Json.parseToJsonElement(s)
        println("Parsed successfully: $json")
        println("Is JsonNull: ${json is JsonNull}")
        json.jsonObject // This should fail
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }
}
