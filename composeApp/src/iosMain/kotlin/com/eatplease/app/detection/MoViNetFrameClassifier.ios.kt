package com.eatplease.app.detection

import cocoapods.TensorFlowLiteC.TfLiteInterpreterCreate
import cocoapods.TensorFlowLiteC.TfLiteInterpreterGetSignatureKey
import cocoapods.TensorFlowLiteC.TfLiteInterpreterGetSignatureRunner
import cocoapods.TensorFlowLiteC.TfLiteInterpreterOptionsCreate
import cocoapods.TensorFlowLiteC.TfLiteInterpreterOptionsDelete
import cocoapods.TensorFlowLiteC.TfLiteInterpreterOptionsSetNumThreads
import cocoapods.TensorFlowLiteC.TfLiteModelCreate
import cocoapods.TensorFlowLiteC.TfLiteSignatureRunnerAllocateTensors
import cocoapods.TensorFlowLiteC.TfLiteSignatureRunnerGetInputCount
import cocoapods.TensorFlowLiteC.TfLiteSignatureRunnerGetInputName
import cocoapods.TensorFlowLiteC.TfLiteSignatureRunnerGetInputTensor
import cocoapods.TensorFlowLiteC.TfLiteSignatureRunnerGetOutputCount
import cocoapods.TensorFlowLiteC.TfLiteSignatureRunnerGetOutputName
import cocoapods.TensorFlowLiteC.TfLiteSignatureRunnerGetOutputTensor
import cocoapods.TensorFlowLiteC.TfLiteSignatureRunnerInvoke
import cocoapods.TensorFlowLiteC.TfLiteTensorByteSize
import cocoapods.TensorFlowLiteC.TfLiteTensorData
import cocoapods.TensorFlowLiteC.TfLiteTensorQuantizationParams
import cocoapods.TensorFlowLiteC.TfLiteTensorType
import cocoapods.TensorFlowLiteC.kTfLiteFloat32
import cocoapods.TensorFlowLiteC.kTfLiteInt8
import cocoapods.TensorFlowLiteC.kTfLiteOk
import cocoapods.TensorFlowLiteC.kTfLiteUInt8
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import platform.posix.memcpy
import platform.posix.memset

/**
 * MoViNet-A0-Stream through the TensorFlow Lite C API (TensorFlowLiteC pod).
 * Mirrors the Android implementation: one frame per invocation, recurrent
 * state outputs copied back into same-named inputs, runtime introspection of
 * dtypes/quantization, logits dequantized and softmaxed.
 */
@OptIn(ExperimentalForeignApi::class)
class MoViNetFrameClassifier(modelBytes: ByteArray) : FrameClassifier {

    private val modelData = nativeHeap.allocArray<ByteVar>(modelBytes.size)
    private val interpreter: kotlinx.cinterop.CPointer<cocoapods.TensorFlowLiteC.TfLiteInterpreter>
    private val runner: kotlinx.cinterop.CPointer<cocoapods.TensorFlowLiteC.TfLiteSignatureRunner>
    private val imageInputName: String
    private val inputNames: List<String>
    private val outputNames: List<String>
    private val logitsOutputName: String

    init {
        modelBytes.usePinned { pinned ->
            memcpy(modelData, pinned.addressOf(0), modelBytes.size.convert())
        }
        val model = TfLiteModelCreate(modelData, modelBytes.size.convert())
            ?: error("Cannot parse the MoViNet model")
        val options = TfLiteInterpreterOptionsCreate()
        TfLiteInterpreterOptionsSetNumThreads(options, 2)
        interpreter = TfLiteInterpreterCreate(model, options)
            ?: error("Cannot create the TFLite interpreter")
        TfLiteInterpreterOptionsDelete(options)

        val signatureKey = TfLiteInterpreterGetSignatureKey(interpreter, 0)?.toKString()
            ?: error("MoViNet model has no signatures")
        runner = TfLiteInterpreterGetSignatureRunner(interpreter, signatureKey)
            ?: error("Cannot create the signature runner")
        check(TfLiteSignatureRunnerAllocateTensors(runner) == kTfLiteOk) {
            "Cannot allocate tensors"
        }

        inputNames = (0 until TfLiteSignatureRunnerGetInputCount(runner).toInt()).mapNotNull {
            TfLiteSignatureRunnerGetInputName(runner, it)?.toKString()
        }
        outputNames = (0 until TfLiteSignatureRunnerGetOutputCount(runner).toInt()).mapNotNull {
            TfLiteSignatureRunnerGetOutputName(runner, it)?.toKString()
        }
        imageInputName = inputNames.firstOrNull { it.contains("image") }
            ?: error("No image input among $inputNames")
        logitsOutputName = outputNames.firstOrNull { it.contains("logits") }
            ?: outputNames.first { it !in inputNames }
    }

    override suspend fun classify(rgbFrame: ByteArray): FloatArray {
        fillImageInput(rgbFrame)

        check(TfLiteSignatureRunnerInvoke(runner) == kTfLiteOk) { "MoViNet invocation failed" }

        // Thread updated recurrent states into the next invocation.
        for (name in outputNames) {
            if (name == logitsOutputName || name !in inputNames) continue
            val output = TfLiteSignatureRunnerGetOutputTensor(runner, name) ?: continue
            val input = TfLiteSignatureRunnerGetInputTensor(runner, name) ?: continue
            memcpy(TfLiteTensorData(input), TfLiteTensorData(output), TfLiteTensorByteSize(output))
        }

        return softmax(readLogits())
    }

    override fun reset() {
        for (name in inputNames) {
            if (name == imageInputName) continue
            val tensor = TfLiteSignatureRunnerGetInputTensor(runner, name) ?: continue
            memset(TfLiteTensorData(tensor), 0, TfLiteTensorByteSize(tensor))
        }
    }

    private fun fillImageInput(rgbFrame: ByteArray) {
        val expected = FrameClassifier.FRAME_SIZE * FrameClassifier.FRAME_SIZE * 3
        require(rgbFrame.size == expected) { "Expected $expected RGB bytes, got ${rgbFrame.size}" }
        val tensor = TfLiteSignatureRunnerGetInputTensor(runner, imageInputName)
            ?: error("Image input tensor missing")
        val data = TfLiteTensorData(tensor) ?: error("Image tensor has no data")
        when (TfLiteTensorType(tensor)) {
            kTfLiteUInt8, kTfLiteInt8 -> rgbFrame.usePinned { pinned ->
                memcpy(data, pinned.addressOf(0), rgbFrame.size.convert())
            }

            kTfLiteFloat32 -> {
                val floats = data.reinterpret<FloatVar>()
                for (i in rgbFrame.indices) {
                    floats[i] = (rgbFrame[i].toInt() and 0xFF) / 255f
                }
            }

            else -> error("Unsupported image input type")
        }
    }

    private fun readLogits(): FloatArray {
        val tensor = TfLiteSignatureRunnerGetOutputTensor(runner, logitsOutputName)
            ?: error("Logits tensor missing")
        val data = TfLiteTensorData(tensor) ?: error("Logits tensor has no data")
        val byteSize = TfLiteTensorByteSize(tensor).toInt()
        return when (TfLiteTensorType(tensor)) {
            kTfLiteFloat32 -> {
                val count = byteSize / 4
                val floats = data.reinterpret<FloatVar>()
                FloatArray(count) { floats[it] }
            }

            kTfLiteUInt8, kTfLiteInt8 -> {
                val signed = TfLiteTensorType(tensor) == kTfLiteInt8
                val bytes = data.reinterpret<ByteVar>()
                TfLiteTensorQuantizationParams(tensor).useContents {
                    FloatArray(byteSize) { i ->
                        val raw = if (signed) bytes[i].toInt() else bytes[i].toInt() and 0xFF
                        scale * (raw - zero_point)
                    }
                }
            }

            else -> error("Unsupported logits type")
        }
    }
}
