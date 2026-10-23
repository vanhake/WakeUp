package de.vanhake.wakeup.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Stores alarms as JSON in device-protected storage, so alarms can be restored and
 * ring after a reboot even before the phone is unlocked.
 */
object AlarmRepository {
    private const val TAG = "AlarmRepository"
    private lateinit var file: JsonFile
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::file.isInitialized) return
        BarcodeRepository.init(context)
        file = JsonFile(context, "alarms.json")
        val json = file.read() ?: return
        try {
            val migrated = migrateInlineBarcodes(json)
            _alarms.value = Alarm.listFromJson(migrated)
            if (migrated != json) file.write(migrated)
        } catch (e: Exception) {
            Log.e(TAG, "Could not parse alarms", e)
        }
    }

    /** Version 1.0 stored the scanned code on the alarm itself; move it into the library. */
    private fun migrateInlineBarcodes(json: String): String {
        val arr = JSONArray(json)
        var changed = false
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.has("barcode") && !o.has("barcodeId")) {
                o.put("barcodeId", BarcodeRepository.add("", o.getString("barcode")).id)
                o.remove("barcode")
                changed = true
            }
        }
        return if (changed) arr.toString() else json
    }

    fun get(id: Int): Alarm? = _alarms.value.firstOrNull { it.id == id }

    @Synchronized
    fun nextId(): Int = (_alarms.value.maxOfOrNull { it.id } ?: 0) + 1

    @Synchronized
    fun upsert(alarm: Alarm) {
        val list = _alarms.value.toMutableList()
        val index = list.indexOfFirst { it.id == alarm.id }
        if (index >= 0) list[index] = alarm else list.add(alarm)
        save(list)
    }

    @Synchronized
    fun delete(id: Int) = save(_alarms.value.filterNot { it.id == id })

    private fun save(list: List<Alarm>) {
        val sorted = list.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
        file.write(Alarm.listToJson(sorted))
        _alarms.value = sorted
    }
}
