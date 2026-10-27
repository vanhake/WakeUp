package de.vanhake.wakeup.sleep

import android.content.Context
import android.util.Log
import de.vanhake.wakeup.data.JsonFile
import de.vanhake.wakeup.sleep.analysis.NightRecording
import de.vanhake.wakeup.sleep.analysis.SleepAnalyzer
import de.vanhake.wakeup.sleep.analysis.SleepReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Short facts about a night for the history list. */
data class NightSummary(
    val start: Long,
    val end: Long,
    val sleepMinutes: Int,
    val snoreMinutes: Int,
    val apneasPerHour: Float?,
    val score: Int,
    val interrupted: Boolean,
    /** [SleepRepository.ANALYSIS_VERSION] the values were computed with. */
    val version: Int = SleepRepository.ANALYSIS_VERSION,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("start", start)
        put("end", end)
        put("sleepMinutes", sleepMinutes)
        put("snoreMinutes", snoreMinutes)
        apneasPerHour?.let { put("apneasPerHour", it.toDouble()) }
        put("score", score)
        put("interrupted", interrupted)
        put("version", version)
    }

    companion object {
        fun fromJson(o: JSONObject) = NightSummary(
            start = o.getLong("start"),
            end = o.getLong("end"),
            sleepMinutes = o.optInt("sleepMinutes"),
            snoreMinutes = o.optInt("snoreMinutes"),
            apneasPerHour = if (o.has("apneasPerHour")) o.getDouble("apneasPerHour").toFloat() else null,
            score = o.optInt("score"),
            interrupted = o.optBoolean("interrupted"),
            version = o.optInt("version", 1),
        )

        fun of(night: NightRecording, report: SleepReport) = NightSummary(
            start = night.start,
            end = night.end,
            sleepMinutes = report.sleepMinutes,
            snoreMinutes = report.snoreMinutes,
            apneasPerHour = report.apneasPerHour,
            score = report.score,
            interrupted = night.interrupted,
        )
    }
}

/** Settings, recorded nights and audio samples of the sleep analysis, all stored on the device only. */
object SleepRepository {
    private const val TAG = "SleepRepository"
    /** Nights whose full recording (and so the detailed report) is kept. */
    private const val MAX_DETAILED_NIGHTS = 120

    /** Nights kept in the history (about ten years); only the summary of older ones. */
    private const val MAX_NIGHTS = 3660

    /** Increase when [SleepAnalyzer] changes, so stored summaries are computed again. */
    const val ANALYSIS_VERSION = 2
    const val CLIP_LIFETIME_MS = 24 * 60 * 60 * 1000L

    private lateinit var settingsFile: JsonFile
    private lateinit var indexFile: JsonFile
    private lateinit var checkpointFile: JsonFile
    private lateinit var appContext: Context
    private lateinit var dir: File
    lateinit var clipDir: File
        private set

    private val _settings = MutableStateFlow(SleepSettings())
    val settings: StateFlow<SleepSettings> = _settings.asStateFlow()

    private val _nights = MutableStateFlow<List<NightSummary>>(emptyList())

    /** Newest first. */
    val nights: StateFlow<List<NightSummary>> = _nights.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::settingsFile.isInitialized) return
        appContext = context.applicationContext
        val base = appContext.createDeviceProtectedStorageContext().filesDir
        dir = File(base, "sleep").apply { mkdirs() }
        clipDir = File(dir, "clips").apply { mkdirs() }
        settingsFile = JsonFile(context, "sleep/settings.json")
        indexFile = JsonFile(context, "sleep/index.json")
        checkpointFile = JsonFile(context, "sleep/current.json")
        _settings.value = settingsFile.read()?.let {
            try {
                SleepSettings.fromJson(JSONObject(it))
            } catch (e: Exception) {
                Log.e(TAG, "Could not parse settings", e)
                null
            }
        } ?: SleepSettings()
        _nights.value = indexFile.read()?.let {
            try {
                val arr = JSONArray(it)
                (0 until arr.length()).map { i -> NightSummary.fromJson(arr.getJSONObject(i)) }
            } catch (e: Exception) {
                Log.e(TAG, "Could not parse night index", e)
                null
            }
        }.orEmpty()
    }

    @Synchronized
    fun updateSettings(settings: SleepSettings) {
        settingsFile.write(settings.toJson().toString())
        _settings.value = settings
    }

    /** Analyses and stores a finished night. Nights shorter than 10 minutes are discarded. */
    @Synchronized
    fun saveNight(night: NightRecording): NightSummary? {
        if (night.end - night.start < 10 * 60_000L) {
            night.clips.forEach { File(clipDir, it.file).delete() }
            return null
        }
        // Analyse what is stored (values rounded in the file), so the summary matches the report.
        val json = night.toJson()
        val summary = NightSummary.of(night, SleepAnalyzer.analyze(NightRecording.fromJson(json)))
        nightFile(night.start).write(json.toString())
        val all = (listOf(summary) + _nights.value.filterNot { it.start == night.start }).sortedByDescending { it.start }
        all.drop(MAX_DETAILED_NIGHTS).filter { nightFile(it.start).exists() }.forEach { deleteFiles(it.start) }
        all.drop(MAX_NIGHTS).forEach { deleteFiles(it.start) }
        saveIndex(all.take(MAX_NIGHTS))
        return summary
    }

    /** Replaces the stored summary if the freshly computed [report] gives other values. */
    @Synchronized
    fun syncSummary(night: NightRecording, report: SleepReport) {
        val summary = NightSummary.of(night, report)
        val list = _nights.value
        if (list.none { it.start == night.start } || summary in list) return
        saveIndex(list.map { if (it.start == night.start) summary else it })
    }

    /**
     * Computes summaries again that were stored by an older analysis or differ from the
     * report (earlier versions analysed the unrounded data). Slow; call off the main thread.
     */
    fun refreshOutdatedSummaries() {
        val outdated = _nights.value.filter { it.version < ANALYSIS_VERSION }
        if (outdated.isEmpty()) return
        val fresh = outdated.associate { old ->
            old.start to (loadNight(old.start)?.let { NightSummary.of(it, SleepAnalyzer.analyze(it)) } ?: old.copy(version = ANALYSIS_VERSION))
        }
        synchronized(this) {
            saveIndex(_nights.value.map { fresh[it.start] ?: it })
        }
    }

    fun loadNight(start: Long): NightRecording? = nightFile(start).read()?.let {
        try {
            NightRecording.fromJson(JSONObject(it))
        } catch (e: Exception) {
            Log.e(TAG, "Could not parse night $start", e)
            null
        }
    }

    @Synchronized
    fun deleteNight(start: Long) {
        deleteFiles(start)
        saveIndex(_nights.value.filterNot { it.start == start })
    }

    fun clipFile(name: String): File = File(clipDir, name)

    /** Stores the recording so far, so a night is not lost if the app gets killed. */
    fun writeCheckpoint(night: NightRecording) {
        try {
            checkpointFile.write(night.toJson().toString())
        } catch (e: Exception) {
            Log.w(TAG, "Could not write checkpoint", e)
        }
    }

    fun clearCheckpoint() = checkpointFile.delete()

    /** Turns the checkpoint of a recording that was killed into a (shortened) night. */
    @Synchronized
    fun recoverCheckpoint() {
        val json = checkpointFile.read() ?: return
        clearCheckpoint()
        try {
            saveNight(NightRecording.fromJson(JSONObject(json)).copy(interrupted = true))
        } catch (e: Exception) {
            Log.e(TAG, "Could not recover interrupted night", e)
        }
    }

    /** Deletes audio samples older than 24 hours; returns when the next one expires, or null. */
    @Synchronized
    fun deleteExpiredClips(now: Long = System.currentTimeMillis()): Long? {
        val files = clipDir.listFiles().orEmpty()
        var next: Long? = null
        for (f in files) {
            val expires = f.lastModified() + CLIP_LIFETIME_MS
            if (expires <= now) {
                f.delete()
            } else {
                next = minOf(next ?: expires, expires)
            }
        }
        return next
    }

    fun deleteAllClips() {
        clipDir.listFiles().orEmpty().forEach { it.delete() }
    }

    private fun nightFile(start: Long) = JsonFile(appContext, "sleep/night-$start.json")

    private fun deleteFiles(start: Long) {
        loadNight(start)?.clips?.forEach { File(clipDir, it.file).delete() }
        nightFile(start).delete()
    }

    private fun saveIndex(list: List<NightSummary>) {
        indexFile.write(JSONArray(list.map { it.toJson() }).toString())
        _nights.value = list
    }
}
