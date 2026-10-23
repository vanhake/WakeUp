package de.vanhake.wakeup.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BarcodeRepository {
    private lateinit var file: JsonFile
    private val _barcodes = MutableStateFlow<List<SavedBarcode>>(emptyList())
    val barcodes: StateFlow<List<SavedBarcode>> = _barcodes.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::file.isInitialized) return
        file = JsonFile(context, "barcodes.json")
        _barcodes.value = file.read()?.let {
            try {
                SavedBarcode.listFromJson(it)
            } catch (e: Exception) {
                Log.e("BarcodeRepository", "Could not parse barcodes", e)
                null
            }
        }.orEmpty()
    }

    fun get(id: Int?): SavedBarcode? = id?.let { _barcodes.value.firstOrNull { b -> b.id == it } }

    fun findByValue(value: String): SavedBarcode? = _barcodes.value.firstOrNull { it.value == value }

    /** Adds a code to the library, or returns the existing entry with the same value. */
    @Synchronized
    fun add(name: String, value: String): SavedBarcode {
        findByValue(value)?.let { return it }
        val barcode = SavedBarcode(
            id = (_barcodes.value.maxOfOrNull { it.id } ?: 0) + 1,
            name = name.ifBlank { defaultName() },
            value = value,
        )
        save(_barcodes.value + barcode)
        return barcode
    }

    @Synchronized
    fun rename(id: Int, name: String) =
        save(_barcodes.value.map { if (it.id == id) it.copy(name = name.ifBlank { it.name }) else it })

    @Synchronized
    fun delete(id: Int) = save(_barcodes.value.filterNot { it.id == id })

    fun defaultName(): String = "Barcode ${_barcodes.value.size + 1}"

    private fun save(list: List<SavedBarcode>) {
        file.write(SavedBarcode.listToJson(list))
        _barcodes.value = list
    }
}
