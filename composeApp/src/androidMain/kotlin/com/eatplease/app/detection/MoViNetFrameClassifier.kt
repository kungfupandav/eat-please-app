package com.eatplease.app.detection

import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MoViNet-A0-Stream on TensorFlow Lite. The streaming model classifies one
 * frame per invocation and threads recurrent state tensors through: every
 * signature output that shares a name with a signature input is copied back
 * as that input for the next frame. Tensor shapes, dtypes, and quantization
 * are introspected at runtime so int8 and float variants both work.
 */
class MoViNetFrameClassifier(modelBytes: ByteArray) : FrameClassifier {

    private val interpreter: Interpreter
    private val signatureKey: String
    private val imageInputName: String
    private val logitsOutputName: String
    private val inputBuffers = mutableMapOf<String, ByteBuffer>()
    private val outputBuffers = mutableMapOf<String, ByteBuffer>()
    private val inputTensors = mutableMapOf<String, Tensor>()
    private val outputTensors = mutableMapOf<String, Tensor>()

    // Serializes interpreter use against [close]: on Stop the service closes the
    // interpreter while a frame may still be mid-flight on a background thread.
    // Holding [lock] across inference makes close wait for it, and [closed] makes
    // any later frame a no-op instead of calling into a closed interpreter.
    private val lock = Any()
    private var closed = false

    init {
        val model = ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder())
        model.put(modelBytes)
        model.rewind()
        interpreter = Interpreter(model, Interpreter.Options().apply { numThreads = 2 })

        signatureKey = interpreter.signatureKeys.firstOrNull()
            ?: error("MoViNet model has no signatures")

        val inputNames = interpreter.getSignatureInputs(signatureKey)
        val outputNames = interpreter.getSignatureOutputs(signatureKey)

        imageInputName = inputNames.firstOrNull { it.contains("image") }
            ?: error("No image input among ${inputNames.toList()}")
        logitsOutputName = outputNames.firstOrNull { it.contains("logits") }
            ?: outputNames.first { it !in inputNames }

        for (name in inputNames) {
            val tensor = interpreter.getInputTensorFromSignature(name, signatureKey)
            inputTensors[name] = tensor
            inputBuffers[name] = ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())
        }
        for (name in outputNames) {
            val tensor = interpreter.getOutputTensorFromSignature(name, signatureKey)
            outputTensors[name] = tensor
            outputBuffers[name] = ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())
        }
    }

    override suspend fun classify(rgbFrame: ByteArray): FloatArray = synchronized(lock) {
        // A frame that arrives after the interpreter is closed (during Stop) is
        // dropped; the session is already ending, so the result is unused.
        if (closed) return@synchronized FloatArray(FrameClassifier.NUM_CLASSES)

        fillImageInput(rgbFrame)

        val inputs = inputBuffers.mapValues { (_, buffer) -> buffer.rewind(); buffer as Any }
        val outputs = outputBuffers.mapValues { (_, buffer) -> buffer.rewind(); buffer as Any }
        interpreter.runSignature(inputs, outputs, signatureKey)

        // Thread the updated recurrent states into the next invocation.
        for ((name, outBuffer) in outputBuffers) {
            val stateInput = inputBuffers[name] ?: continue
            outBuffer.rewind()
            stateInput.rewind()
            stateInput.put(outBuffer)
        }

        softmax(readLogits())
    }

    override fun reset() = synchronized(lock) {
        if (closed) return
        for ((name, buffer) in inputBuffers) {
            if (name == imageInputName) continue
            buffer.rewind()
            while (buffer.hasRemaining()) buffer.put(0)
        }
    }

    private fun fillImageInput(rgbFrame: ByteArray) {
        val expected = FrameClassifier.FRAME_SIZE * FrameClassifier.FRAME_SIZE * 3
        require(rgbFrame.size == expected) { "Expected $expected RGB bytes, got ${rgbFrame.size}" }
        val buffer = inputBuffers.getValue(imageInputName)
        buffer.rewind()
        when (inputTensors.getValue(imageInputName).dataType()) {
            DataType.UINT8, DataType.INT8 -> buffer.put(rgbFrame)
            DataType.FLOAT32 -> for (byte in rgbFrame) buffer.putFloat((byte.toInt() and 0xFF) / 255f)
            else -> error("Unsupported image input type")
        }
    }

    fun close() = synchronized(lock) {
        if (closed) return
        closed = true
        interpreter.close()
    }

    private fun readLogits(): FloatArray {
        val tensor = outputTensors.getValue(logitsOutputName)
        val buffer = outputBuffers.getValue(logitsOutputName)
        buffer.rewind()
        val count = tensor.numElements()
        val logits = FloatArray(count)
        when (tensor.dataType()) {
            DataType.FLOAT32 -> buffer.asFloatBuffer().get(logits)
            DataType.UINT8, DataType.INT8 -> {
                val quant = tensor.quantizationParams()
                val signed = tensor.dataType() == DataType.INT8
                for (i in 0 until count) {
                    val raw = if (signed) buffer.get(i).toInt() else buffer.get(i).toInt() and 0xFF
                    logits[i] = quant.scale * (raw - quant.zeroPoint)
                }
            }
            else -> error("Unsupported logits type")
        }
        return logits
    }
}
