package de.vanhake.wakeup.data

import org.json.JSONArray
import org.json.JSONObject

/** A barcode / QR code from the central library that alarms can require to be scanned. */
data class SavedBarcode(val id: Int, val name: String, val value: String) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("value", value)
    }

    companion object {
        fun fromJson(o: JSONObject) = SavedBarcode(
            id = o.getInt("id"),
            name = o.optString("name", ""),
            value = o.getString("value"),
        )

        fun listToJson(list: List<SavedBarcode>): String = JSONArray(list.map { it.toJson() }).toString()

        fun listFromJson(json: String): List<SavedBarcode> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }
    }
}
