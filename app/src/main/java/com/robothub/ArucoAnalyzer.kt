package com.robothub

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream

class ArucoAnalyzer(
    private val onStableId: (Int) -> Unit,
    private val onJpegFrame: (ByteArray) -> Unit
) : ImageAnalysis.Analyzer {

    private val stabilizer = IdStabilizer(requiredFrames = 3)

    override fun analyze(image: ImageProxy) {
        try {
            // 1. Deteksi ArUco dari Y-plane (grayscale)
            detectAruco(image)

            // 2. Kirim frame asli ke driver sebagai JPEG
            // Dipanggil sebelum image.close(), karena buffer-nya hanya valid selama frame ini
            try {
                onJpegFrame(yuvToJpeg(image))
            } catch (e: Exception) {
                Log.w("ArucoAnalyzer", "Gagal encode JPEG: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("ArucoAnalyzer", "Gagal menganalisis frame", e)
        } finally {
            image.close()
        }
    }

    private fun detectAruco(image: ImageProxy) {
        val width = image.width
        val height = image.height
        val grayMat = Mat(height, width, CvType.CV_8UC1)
        val corners = mutableListOf<Mat>()
        val ids = Mat()
        val rejected = mutableListOf<Mat>()

        try {
            val plane = image.planes[0]
            val buf = plane.buffer.duplicate()
            val yData = ByteArray(width * height)

            if (plane.pixelStride == 1) {
                for (row in 0 until height) {
                    buf.position(row * plane.rowStride)
                    buf.get(yData, row * width, width)
                }
            } else {
                for (row in 0 until height) {
                    for (col in 0 until width) {
                        yData[row * width + col] =
                            buf.get(row * plane.rowStride + col * plane.pixelStride)
                    }
                }
            }

            grayMat.put(0, 0, yData)
            ArucoConfig.detector.detectMarkers(grayMat, corners, ids, rejected)

            val seen: Int? = if (!ids.empty()) ids.get(0, 0)[0].toInt() else null
            stabilizer.update(seen)?.let { stableId ->
                Log.i("ArucoAnalyzer", "ID stabil: $stableId")
                onStableId(stableId)
            }
        } finally {
            corners.forEach { it.release() }
            rejected.forEach { it.release() }
            ids.release()
            grayMat.release()
        }
    }

    private fun yuvToJpeg(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val nv21 = ByteArray(width * height * 3 / 2)
        var pos = 0

        // Y plane
        val yBuf = yPlane.buffer.duplicate()
        for (row in 0 until height) {
            yBuf.position(row * yPlane.rowStride)
            yBuf.get(nv21, pos, width)
            pos += width
        }

        // VU interleaved (format NV21)
        val uBuf = uPlane.buffer.duplicate()
        val vBuf = vPlane.buffer.duplicate()
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val uIdx = row * uPlane.rowStride + col * uPlane.pixelStride
                val vIdx = row * vPlane.rowStride + col * vPlane.pixelStride
                nv21[pos++] = vBuf.get(vIdx)
                nv21[pos++] = uBuf.get(uIdx)
            }
        }

        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, width, height), 60, out)
        return out.toByteArray()
    }
}