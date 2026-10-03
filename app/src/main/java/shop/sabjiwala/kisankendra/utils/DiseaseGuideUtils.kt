package shop.sabjiwala.kisankendra.utils

data class CropDisease(
    val id: String,
    val name: String,
    val affectedCrops: String,
    val symptoms: String,
    val remedyChemical: String,
    val dosage: String,
    val organicRemedy: String
)

object DiseaseGuideUtils {

    val diseaseList = listOf(
        CropDisease(
            id = "d1",
            name = "Yellow Rust",
            affectedCrops = "Wheat, Barley",
            symptoms = "Yellow linear stripe powder on upper leaf surface. Turns hands yellow on touch.",
            remedyChemical = "Propiconazole 25% EC (Tilt / Bumper)",
            dosage = "1 ml per Litre Water (200 ml in 200L Water per Acre)",
            organicRemedy = "Sour Buttermilk 5 Litres in 150L Water spray."
        ),
        CropDisease(
            id = "d2",
            name = "Stem Borer",
            affectedCrops = "Paddy / Rice, Maize, Sugarcane",
            symptoms = "Central leaf dries up ('Dead Heart'). White dead panicles before harvest.",
            remedyChemical = "Chlorantraniliprole 18.5% SC (Coragen) OR Cartap Hydrochloride 4G",
            dosage = "Coragen @ 60 ml in 150L Water per Acre OR Cartap @ 7.5 kg granules/Acre",
            organicRemedy = "Trichogramma egg parasitoids 20,000 cards per Acre or Neem Cake @ 100 kg/Acre."
        ),
        CropDisease(
            id = "d3",
            name = "Whitefly & Leaf Curl Virus",
            affectedCrops = "Cotton, Tomato, Chilli, Soybean",
            symptoms = "Tiny white flying insects under leaves. Leaves curl upwards and turn sticky.",
            remedyChemical = "Diafenthiuron 50% WP (Polo) OR Pyriproxyfen 10% EC",
            dosage = "Polo @ 250 g in 150L Water per Acre",
            organicRemedy = "Yellow Sticky Traps (8 traps/Acre) + Neem Oil 10,000 PPM @ 3ml/L Water."
        ),
        CropDisease(
            id = "d4",
            name = "Late Blight",
            affectedCrops = "Potato, Tomato",
            symptoms = "Water-soaked dark brown spots on leaf tips and margins. White cottony fungus under leaves.",
            remedyChemical = "Mancozeb 75% WP (Indofil M-45) OR Cymoxanil + Mancozeb (Curzate)",
            dosage = "M-45 @ 600 g per Acre OR Curzate @ 600 g in 200L Water per Acre",
            organicRemedy = "Trichoderma viride @ 5 g/L Water or Copper Oxychloride 50% WP @ 3 g/L."
        ),
        CropDisease(
            id = "d5",
            name = "Wilt & Root Rot",
            affectedCrops = "Gram / Chickpea, Arhar / Pigeonpea, Cotton, Chilli",
            symptoms = "Plants suddenly wilt and dry up in patches. Internal stem vascular bundle turns dark brown.",
            remedyChemical = "Carbendazim 50% WP (Bavistin) seed treatment or soil drenching",
            dosage = "Seed treatment @ 2g/kg seed OR Soil drenching @ 2g/L Water",
            organicRemedy = "Trichoderma harzianum @ 2 kg mixed with 100 kg FYM/Compost applied at sowing."
        ),
        CropDisease(
            id = "d6",
            name = "Aphids",
            affectedCrops = "Mustard, Wheat, Vegetables",
            symptoms = "Small green/black sap-sucking insects clustered on young shoots and flowers.",
            remedyChemical = "Imidacloprid 17.8% SL (Confidor) OR Thiamethoxam 25% WG",
            dosage = "Confidor @ 50 ml in 150L Water per Acre OR Thiamethoxam @ 40 g/Acre",
            organicRemedy = "5% Neem Seed Kernel Extract (NSKE) spray or Garlic-Chilli Extract spray."
        )
    )
}
