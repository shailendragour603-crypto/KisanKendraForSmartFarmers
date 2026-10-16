package shop.sabjiwala.kisankendra.utils

import java.text.DecimalFormat

data class CropFertilizerResult(
    val cropName: String,
    val areaInAcres: Double,
    val seedKg: String,
    val ureaBags: String,
    val dapBags: String,
    val npkBags: String,
    val zincKg: String,
    val recommendationNote: String
)

object FertilizerUtils {

    val crops = listOf(
        "गेहूँ",
        "धान / चावल",
        "सोयाबीन",
        "कपास",
        "सरसों",
        "मक्का",
        "चना",
        "आलू",
        "गन्ना",
        "प्याज़"
    )

    val units = listOf("Acre", "Bigha", "Hectare")

    fun calculate(cropIndex: Int, rawArea: Double, unitIndex: Int): CropFertilizerResult {
        val areaInAcres = when (unitIndex) {
            1 -> rawArea / 2.5 // Bigha to Acre
            2 -> rawArea * 2.47 // Hectare to Acre
            else -> rawArea // Acre
        }.coerceAtLeast(0.1)

        val df = DecimalFormat("#.#")

        return when (cropIndex) {
            0 -> // Wheat
                CropFertilizerResult(
                    cropName = "Wheat",
                    areaInAcres = areaInAcres,
                    seedKg = "${df.format(areaInAcres * 40)} kg",
                    ureaBags = "${df.format(areaInAcres * 1.5)} Bags (75 kg)",
                    dapBags = "${df.format(areaInAcres * 1.0)} Bag (50 kg)",
                    npkBags = "${df.format(areaInAcres * 1.0)} Bag (50 kg)",
                    zincKg = "${df.format(areaInAcres * 5.0)} kg",
                    recommendationNote = "Apply 100% DAP & NPK + 50% Urea at sowing time. Apply remaining 50% Urea at first irrigation (21 days)."
                )

            1 -> // Paddy
                CropFertilizerResult(
                    cropName = "Paddy / Rice",
                    areaInAcres = areaInAcres,
                    seedKg = "${df.format(areaInAcres * 10)} kg",
                    ureaBags = "${df.format(areaInAcres * 2.0)} Bags (100 kg)",
                    dapBags = "${df.format(areaInAcres * 1.0)} Bag (50 kg)",
                    npkBags = "${df.format(areaInAcres * 1.0)} Bag (50 kg)",
                    zincKg = "${df.format(areaInAcres * 10.0)} kg Zinc Sulphate",
                    recommendationNote = "Zinc application is critical for Paddy to prevent Khaira disease. Apply Zinc 10 days after transplanting."
                )

            2 -> // Soybean
                CropFertilizerResult(
                    cropName = "Soybean",
                    areaInAcres = areaInAcres,
                    seedKg = "${df.format(areaInAcres * 35)} kg",
                    ureaBags = "${df.format(areaInAcres * 0.5)} Bag (25 kg)",
                    dapBags = "${df.format(areaInAcres * 1.0)} Bag (50 kg)",
                    npkBags = "${df.format(areaInAcres * 1.0)} Bag (50 kg)",
                    zincKg = "${df.format(areaInAcres * 5.0)} kg",
                    recommendationNote = "Legume crop: Uses nitrogen fixation. Keep Urea minimal to avoid rank vegetative growth."
                )

            3 -> // Cotton
                CropFertilizerResult(
                    cropName = "Cotton",
                    areaInAcres = areaInAcres,
                    seedKg = "${df.format(areaInAcres * 1.5)} Packet (1.5 kg)",
                    ureaBags = "${df.format(areaInAcres * 2.5)} Bags (125 kg)",
                    dapBags = "${df.format(areaInAcres * 1.0)} Bag (50 kg)",
                    npkBags = "${df.format(areaInAcres * 1.2)} Bags (60 kg)",
                    zincKg = "${df.format(areaInAcres * 8.0)} kg",
                    recommendationNote = "Split Urea into 3 doses: Sowing, Square Formation (35 days), and Flowering (60 days)."
                )

            4 -> // Mustard
                CropFertilizerResult(
                    cropName = "Mustard",
                    areaInAcres = areaInAcres,
                    seedKg = "${df.format(areaInAcres * 2.0)} kg",
                    ureaBags = "${df.format(areaInAcres * 1.2)} Bags (60 kg)",
                    dapBags = "${df.format(areaInAcres * 0.8)} Bag (40 kg)",
                    npkBags = "${df.format(areaInAcres * 0.8)} Bag (40 kg)",
                    zincKg = "${df.format(areaInAcres * 10.0)} kg Sulphur 90%",
                    recommendationNote = "Sulphur is essential for oilseeds to increase oil content percentage and grain weight."
                )

            else -> // General Crop
                CropFertilizerResult(
                    cropName = crops.getOrNull(cropIndex) ?: "General Crop",
                    areaInAcres = areaInAcres,
                    seedKg = "${df.format(areaInAcres * 25)} kg",
                    ureaBags = "${df.format(areaInAcres * 1.5)} Bags (75 kg)",
                    dapBags = "${df.format(areaInAcres * 1.0)} Bag (50 kg)",
                    npkBags = "${df.format(areaInAcres * 1.0)} Bag (50 kg)",
                    zincKg = "${df.format(areaInAcres * 5.0)} kg",
                    recommendationNote = "Balanced NPK ratio 4:2:1 with micronutrients ensures optimal root development and yield."
                )
        }
    }
}
