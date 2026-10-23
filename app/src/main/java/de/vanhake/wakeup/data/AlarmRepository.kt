package de.vanhake.wakeup.data

import android.content.Context
import android.util.Log
import androidx.core.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Stores alarms as JSON in device-protected storage, so alarms can be restored and
 * ring after a reboot even before the phone is unlocked.
 */
object AlarmRepository {
    private const val TAG = "AlarmRepository"
    private lateinit var file: AtomicFile
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::file.isInitialized) return
        val storage = context.applicationContext.createDeviceProtectedStorageContext()
        file = AtomicFile(File(storage.filesDir, "alarms.json"))
        _alarms.value = try {
            Alarm.listFromJson(String(file.readFully(), Charsets.UTF_8))
        } catch (e: java.io.FileNotFoundException) {
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Could not read alarms", e)
            emptyList()
        }
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
        val out = file.startWrite()
        try {
            out.write(Alarm.listToJson(sorted).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
        _alarms.value = sorted
    }
}
