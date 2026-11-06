package com.robothub

import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Dictionary
import org.opencv.objdetect.Objdetect

object ArucoConfig {

    val DICTIONARY_ID = Objdetect.DICT_5X5_50

    val dictionary: Dictionary by lazy {
        Objdetect.getPredefinedDictionary(DICTIONARY_ID)
    }

    val detector: ArucoDetector by lazy {
        val params = DetectorParameters().apply {
            set_cornerRefinementMethod(Objdetect.CORNER_REFINE_SUBPIX)
            set_minMarkerPerimeterRate(0.01)
            set_adaptiveThreshWinSizeMin(3)
            set_adaptiveThreshWinSizeMax(53)
            set_adaptiveThreshWinSizeStep(4)
        }
        ArucoDetector(dictionary, params)
    }

    val DESTINATIONS = mapOf(
        0 to "BOX_A",
        1 to "BOX_B",
        2 to "BOX_C",
        3 to "BOX_D"
    )
}