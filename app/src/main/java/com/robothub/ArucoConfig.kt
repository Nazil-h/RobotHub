package com.robothub

import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Objdetect

object ArucoConfig {

    data class DictDetector(val name: String, val detector: ArucoDetector)

    private fun createParams(): DetectorParameters {
        return DetectorParameters().apply {
            set_cornerRefinementMethod(Objdetect.CORNER_REFINE_SUBPIX)
            set_minMarkerPerimeterRate(0.01)
            set_adaptiveThreshWinSizeMin(3)
            set_adaptiveThreshWinSizeMax(53)
            set_adaptiveThreshWinSizeStep(4)
        }
    }

    // Mendukung otomatis berbagai kamus populer: 4x4, 5x5, dan 6x6
    val detectors: List<DictDetector> by lazy {
        val params = createParams()
        listOf(
            DictDetector("4X4_50", ArucoDetector(Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50), params)),
            DictDetector("4X4_250", ArucoDetector(Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_250), params)),
            DictDetector("5X5_50", ArucoDetector(Objdetect.getPredefinedDictionary(Objdetect.DICT_5X5_50), params)),
            DictDetector("5X5_250", ArucoDetector(Objdetect.getPredefinedDictionary(Objdetect.DICT_5X5_250), params)),
            DictDetector("6X6_50", ArucoDetector(Objdetect.getPredefinedDictionary(Objdetect.DICT_6X6_50), params)),
            DictDetector("6X6_250", ArucoDetector(Objdetect.getPredefinedDictionary(Objdetect.DICT_6X6_250), params)),
        )
    }

    val detector: ArucoDetector by lazy {
        detectors.first { it.name == "5X5_50" }.detector
    }

    val DESTINATIONS = mapOf(
        0 to "BOX_A",
        1 to "BOX_B",
        2 to "BOX_C",
        3 to "BOX_D"
    )
}
