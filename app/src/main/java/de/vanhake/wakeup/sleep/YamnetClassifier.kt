package de.vanhake.wakeup.sleep

import android.content.Context
import android.util.Log
import de.vanhake.wakeup.sleep.analysis.YAMNET_WINDOW
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Google's YAMNet sound classifier (AudioSet, 521 classes), run offline with TensorFlow Lite.
 * Input: 15600 samples of 16-kHz mono audio in -1..1; output: one score per class.
 */
class YamnetClassifier private constructor(private val interpreter: Interpreter) {
    private val output = arrayOf(FloatArray(CLASSES))

    fun classify(samples: FloatArray): FloatArray {
        require(samples.size == YAMNET_WINDOW)
        interpreter.run(samples, output)
        return output[0]
    }

    fun close() = interpreter.close()

    companion object {
        private const val CLASSES = 521
        private const val MODEL = "yamnet.tflite"

        /** Loads the model from the app's assets; null if that is not possible on this device. */
        fun create(context: Context): YamnetClassifier? = try {
            val bytes = context.assets.open(MODEL).use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
            model.rewind()
            YamnetClassifier(Interpreter(model, Interpreter.Options().setNumThreads(1)))
        } catch (e: Throwable) {
            Log.w("YamnetClassifier", "Sound classifier not available", e)
            null
        }
    }
}
