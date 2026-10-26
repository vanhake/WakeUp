package de.vanhake.wakeup.data

import android.content.Context
import android.util.Log
import androidx.core.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

/** Atomically written JSON file in device-protected storage (readable before first unlock). */
class JsonFile(context: Context, name: String) {
    private val file = AtomicFile(
        File(context.applicationContext.createDeviceProtectedStorageContext().filesDir, name),
    )

    /** File content, or null if it does not exist or cannot be read. */
    fun read(): String? = try {
        String(file.readFully(), Charsets.UTF_8)
    } catch (e: FileNotFoundException) {
        null
    } catch (e: Exception) {
        Log.e("JsonFile", "Could not read ${file.baseFile}", e)
        null
    }

    fun delete() = file.delete()

    fun write(json: String) {
        val out = file.startWrite()
        try {
            out.write(json.toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }
}
