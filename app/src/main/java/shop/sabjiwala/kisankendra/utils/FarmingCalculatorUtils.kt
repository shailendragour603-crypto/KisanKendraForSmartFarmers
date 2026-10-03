package shop.sabjiwala.kisankendra.utils

import java.text.DecimalFormat
import kotlin.math.ceil

// Result Data Classes for the 10 Core Calculators

data class FieldPrepResult(
    val cropName: String,
    val areaInAcres: Double,
    val operationName: String,
    val machineName: String,
    val rateText: String,
    val totalCost: Double,
    val costPerAcre: Double
)

data class SowingResult(
    val cropName: String,
    val variety: String,
    val areaInAcres: Double,
    val sowingMethod: String,
    val seedRatePerAcre: Double,
    val totalSeedKg: Double,
    val seedPricePerKg: Double,
    val totalSeedCost: Double
)

data class FertilizerPlanResult(
    val cropName: String,
    val areaInAcres: Double,
    val ureaBags: Double,
    val ureaKg: Double,
    val dapBags: Double,
    val dapKg: Double,
    val mopBags: Double,
    val mopKg: Double,
    val zincKg: Double,
    val totalCost: Double,
    val applicationSchedule: String
)

data class IrrigationResult(
    val cropName: String,
    val areaInAcres: Double,
    val stageName: String,
    val method: String,
    val waterPerIrrigationLiters: Double,
    val numIrrigations: Int,
    val totalSeasonalWaterLiters: Double,
    val totalCost: Double,
    val costPerAcre: Double
)

data class SprayResult(
    val cropName: String,
    val areaInAcres: Double,
    val problemCategory: String,
    val productName: String,
    val waterRequirementLiters: Double,
    val tankCapacityLiters: Double,
    val tankLoads: Int,
    val totalProductRequired: Double, // in ml or grams
    val productPerTank: Double,       // in ml or grams
    val isLiquid: Boolean,
    val totalCost: Double,
    val labelGuidance: String
)

data class LabourResult(
    val workType: String,
    val areaInAcres: Double,
    val numWorkers: Int,
    val duration: Double, // days or hours
    val isHourly: Boolean,
    val wageRate: Double,
    val totalLabourCost: Double,
    val costPerAcre: Double
)

data class HarvestingResult(
    val cropName: String,
    val areaInAcres: Double,
    val method: String, // Manual or Combine
    val totalCost: Double,
    val costPerAcre: Double,
    val detailsText: String
)

data class PostHarvestResult(
    val cropName: String,
    val quantityQuintals: Double,
    val transportCost: Double,
    val loadingCost: Double,
    val unloadingCost: Double,
    val storageCost: Double,
    val totalCost: Double
)

data class CropSaleResult(
    val cropName: String,
    val quantityQuintals: Double,
    val mandiPricePerQ: Double,
    val grossSale: Double,
    val commissionDeduction: Double,
    val marketChargesDeduction: Double,
    val transportDeduction: Double,
    val otherDeduction: Double,
    val totalDeductions: Double,
    val netSale: Double
)

data class CropProfitResult(
    val cropName: String,
    val areaInAcres: Double,
    val expectedYieldPerAcre: Double,
    val totalYieldQuintals: Double,
    val expectedPricePerQuintal: Double,
    val expectedRevenue: Double,
    val seedCost: Double,
    val machineryCost: Double,
    val fertilizerCost: Double,
    val irrigationCost: Double,
    val sprayCost: Double,
    val labourCost: Double,
    val harvestingCost: Double,
    val postHarvestCost: Double,
    val totalCost: Double,
    val netProfit: Double,
    val profitPerAcre: Double,
    val breakEvenPricePerQuintal: Double,
    val roiPercent: Double
)

object FarmingCalculatorUtils {

    val crops = listOf(
        "Wheat",
        "Paddy / Rice",
        "Soybean",
        "Cotton",
        "Mustard",
        "Maize",
        "Gram / Chickpea",
        "Potato",
        "Sugarcane",
        "Onion",
        "General Crop"
    )

    val units = listOf("Acre", "Bigha", "Hectare")

    val calculatorCategories = listOf(
        "🌱 Production Stage",
        "📦 Post-Harvest Stage",
        "Financial Stage"
    )

    val allCalculators = listOf(
        // Production
        "1. 🚜 Farm Machinery & Field Preparation",
        "2. 🌱 Sowing & Seed Calculator",
        "3. 🧪 Fertilizer & Nutrient Calculator",
        "4. 💧 Irrigation & Water Calculator",
        "5. 💊 Crop Protection & Spray Calculator",
        "6. 👨‍🌾 Labour & Worker Cost Calculator",
        "7. 🌾 Harvesting Calculator",
        // Post-Harvest
        "8. 📦 Post-Harvest, Transport & Storage",
        "9. 🏪 Crop Sale & Mandi Calculator",
        // Financial
        "10. Complete Crop Profit & ROI Calculator"
    )

    fun toAcres(rawArea: Double, unitIndex: Int): Double {
        val area = rawArea.coerceAtLeast(0.01)
        return when (unitIndex) {
            1 -> area / 2.5     // Bigha to Acre
            2 -> area * 2.47    // Hectare to Acre
            else -> area        // Acre
        }
    }

    private fun df(value: Double, pattern: String = "#,##0.#"): String {
        return DecimalFormat(pattern).format(value)
    }

    // 1. Farm Machinery & Field Preparation Calculator
    fun calculateFieldPrep(
        cropName: String,
        rawArea: Double,
        unitIndex: Int,
        operation: String,
        machine: String,
        rate: Double,
        isHourly: Boolean,
        hoursPerAcre: Double,
        fuelSeparate: Boolean,
        fuelConsLPerAcre: Double,
        fuelPrice: Double
    ): FieldPrepResult {
        val acres = toAcres(rawArea, unitIndex)
        val baseCost = if (isHourly) {
            acres * hoursPerAcre * rate
        } else {
            acres * rate
        }

        val fuelCost = if (fuelSeparate) {
            acres * fuelConsLPerAcre * fuelPrice
        } else {
            0.0
        }

        val totalCost = baseCost + fuelCost
        val costPerAcre = if (acres > 0) totalCost / acres else totalCost

        val rateText = if (isHourly) {
            "₹${df(rate)}/Hour (${df(hoursPerAcre)} hrs/Acre)"
        } else {
            "₹${df(rate)}/Acre"
        }

        return FieldPrepResult(
            cropName = cropName,
            areaInAcres = acres,
            operationName = operation,
            machineName = machine,
            rateText = rateText,
            totalCost = totalCost,
            costPerAcre = costPerAcre
        )
    }

    // 2. Sowing & Seed Calculator
    fun calculateSowing(
        cropIndex: Int,
        variety: String,
        rawArea: Double,
        unitIndex: Int,
        sowingMethod: String,
        customSeedRateKg: Double?,
        seedPricePerKg: Double
    ): SowingResult {
        val acres = toAcres(rawArea, unitIndex)
        val crop = crops.getOrElse(cropIndex) { "Wheat" }

        val defaultSeedRate = when (cropIndex) {
            0 -> if (sowingMethod.contains("Broadcasting")) 45.0 else 40.0 // Wheat
            1 -> if (sowingMethod.contains("Transplanting")) 10.0 else 20.0 // Paddy
            2 -> 35.0 // Soybean
            3 -> 1.5  // Cotton (pack/kg)
            4 -> 2.0  // Mustard
            5 -> 8.0  // Maize
            6 -> 30.0 // Gram
            7 -> 1200.0 // Potato tubers
            8 -> 3000.0 // Sugarcane setts
            9 -> 4.0  // Onion
            else -> 25.0
        }

        val finalSeedRate = customSeedRateKg ?: defaultSeedRate
        val totalSeedKg = acres * finalSeedRate
        val totalCost = totalSeedKg * seedPricePerKg

        return SowingResult(
            cropName = crop,
            variety = variety.ifBlank { "Standard Variety" },
            areaInAcres = acres,
            sowingMethod = sowingMethod,
            seedRatePerAcre = finalSeedRate,
            totalSeedKg = totalSeedKg,
            seedPricePerKg = seedPricePerKg,
            totalSeedCost = totalCost
        )
    }

    // 3. Fertilizer & Nutrient Calculator
    fun calculateFertilizer(
        cropIndex: Int,
        rawArea: Double,
        unitIndex: Int,
        ureaPriceBag: Double = 266.0,
        dapPriceBag: Double = 1350.0,
        mopPriceBag: Double = 1700.0,
        zincPriceKg: Double = 80.0
    ): FertilizerPlanResult {
        val acres = toAcres(rawArea, unitIndex)
        val crop = crops.getOrElse(cropIndex) { "Wheat" }

        val (ureaBagsPerAcre, dapBagsPerAcre, mopBagsPerAcre, zincKgPerAcre, schedule) = when (cropIndex) {
            0 -> Tuple5(1.5, 1.0, 0.5, 5.0, "At Sowing: 100% DAP & MOP + 50% Urea.\n1st Irrigation (21 days): 50% Urea + Zinc.")
            1 -> Tuple5(2.0, 1.0, 0.6, 10.0, "At Transplanting: 100% DAP + 33% Urea.\n10 Days After Transplanting: Zinc Sulphate.\nTillering & Panicle Stage: Remaining Urea in 2 splits.")
            2 -> Tuple5(0.5, 1.0, 0.5, 5.0, "Legume Crop: Apply 100% DAP, MOP & Zinc at sowing. Urea kept minimal for nitrogen fixation.")
            3 -> Tuple5(2.5, 1.0, 1.0, 8.0, "At Sowing: 100% DAP & MOP.\nUrea Split: 35 days (Square formation) & 60 days (Flowering).")
            4 -> Tuple5(1.2, 0.8, 0.4, 10.0, "Oilseed: Apply 100% DAP & MOP + 50% Urea at basal. Apply Sulphur/Zinc for oil yield.")
            5 -> Tuple5(2.0, 1.0, 0.8, 5.0, "Basal: 100% DAP, MOP + 33% Urea.\nKnee-High & Flowering Stage: Remaining Urea in 2 doses.")
            6 -> Tuple5(0.3, 1.0, 0.5, 4.0, "Pulse Crop: Apply DAP & MOP at sowing. Minimal Nitrogen needed.")
            7 -> Tuple5(3.0, 2.0, 1.5, 10.0, "Basal: 100% DAP & MOP + 50% Urea.\nEarthing Up (30 days): Remaining 50% Urea.")
            8 -> Tuple5(4.0, 2.0, 1.5, 10.0, "Basal: DAP, MOP + 25% Urea.\nSplit Urea at 45, 90 & 120 days during grand growth.")
            else -> Tuple5(1.5, 1.0, 0.5, 5.0, "Basal: 100% DAP & MOP + 50% Urea. Top dress remaining Urea at 1st irrigation.")
        }

        val totalUreaBags = acres * ureaBagsPerAcre
        val totalUreaKg = totalUreaBags * 50.0
        val totalDapBags = acres * dapBagsPerAcre
        val totalDapKg = totalDapBags * 50.0
        val totalMopBags = acres * mopBagsPerAcre
        val totalMopKg = totalMopBags * 50.0
        val totalZincKg = acres * zincKgPerAcre

        val totalCost = (totalUreaBags * ureaPriceBag) +
                (totalDapBags * dapPriceBag) +
                (totalMopBags * mopPriceBag) +
                (totalZincKg * zincPriceKg)

        return FertilizerPlanResult(
            cropName = crop,
            areaInAcres = acres,
            ureaBags = totalUreaBags,
            ureaKg = totalUreaKg,
            dapBags = totalDapBags,
            dapKg = totalDapKg,
            mopBags = totalMopBags,
            mopKg = totalMopKg,
            zincKg = totalZincKg,
            totalCost = totalCost,
            applicationSchedule = schedule
        )
    }

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    // 4. Irrigation & Water Calculator
    fun calculateIrrigation(
        cropIndex: Int,
        rawArea: Double,
        unitIndex: Int,
        stageName: String,
        method: String,
        numIrrigations: Int,
        pumpType: String, // "Electric Pump", "Diesel Pump", "Canal Water"
        hp: Double,
        hoursPerIrrigation: Double,
        electricityRate: Double, // ₹/kWh
        fuelConsLHr: Double,    // L/hr
        fuelPrice: Double,      // ₹/L
        directCanalCost: Double
    ): IrrigationResult {
        val acres = toAcres(rawArea, unitIndex)
        val crop = crops.getOrElse(cropIndex) { "Wheat" }

        val waterPerIrrigationPerAcreLiters = when {
            method.contains("Drip", ignoreCase = true) -> 100000.0
            method.contains("Sprinkler", ignoreCase = true) -> 120000.0
            else -> 200000.0 // Flood irrigation
        }

        val singleWaterTotal = acres * waterPerIrrigationPerAcreLiters
        val totalSeasonalWater = singleWaterTotal * numIrrigations

        val singleIrrigationCost = when {
            pumpType.contains("Electric", ignoreCase = true) -> {
                // kWh = HP * 0.746 * Hours
                val kWh = hp * 0.746 * hoursPerIrrigation
                kWh * electricityRate
            }
            pumpType.contains("Diesel", ignoreCase = true) -> {
                val liters = fuelConsLHr * hoursPerIrrigation
                liters * fuelPrice
            }
            else -> { // Canal
                directCanalCost / (if (numIrrigations > 0) numIrrigations else 1)
            }
        }

        val totalCost = singleIrrigationCost * numIrrigations
        val costPerAcre = if (acres > 0) totalCost / acres else totalCost

        return IrrigationResult(
            cropName = crop,
            areaInAcres = acres,
            stageName = stageName,
            method = method,
            waterPerIrrigationLiters = singleWaterTotal,
            numIrrigations = numIrrigations,
            totalSeasonalWaterLiters = totalSeasonalWater,
            totalCost = totalCost,
            costPerAcre = costPerAcre
        )
    }

    // 5. Crop Protection & Spray Calculator
    fun calculateSpray(
        cropIndex: Int,
        problemCategory: String,
        productName: String,
        dosePerLiterMl: Double,
        rawArea: Double,
        unitIndex: Int,
        tankCapacityLiters: Double = 15.0,
        waterPerAcreLiters: Double = 150.0,
        productPricePerUnit: Double = 500.0, // price per 500ml/g
        packageSizeUnits: Double = 500.0     // 500ml/g
    ): SprayResult {
        val acres = toAcres(rawArea, unitIndex)
        val crop = crops.getOrElse(cropIndex) { "Wheat" }

        val totalWaterReqLiters = acres * waterPerAcreLiters
        val tankLoads = ceil(totalWaterReqLiters / tankCapacityLiters).toInt().coerceAtLeast(1)

        val totalProductReq = totalWaterReqLiters * dosePerLiterMl
        val productPerTank = tankCapacityLiters * dosePerLiterMl

        val totalCost = if (packageSizeUnits > 0) {
            (totalProductReq / packageSizeUnits) * productPricePerUnit
        } else {
            0.0
        }

        return SprayResult(
            cropName = crop,
            areaInAcres = acres,
            problemCategory = problemCategory,
            productName = productName.ifBlank { "Recommended Pesticide/Fungicide" },
            waterRequirementLiters = totalWaterReqLiters,
            tankCapacityLiters = tankCapacityLiters,
            tankLoads = tankLoads,
            totalProductRequired = totalProductReq,
            productPerTank = productPerTank,
            isLiquid = true,
            totalCost = totalCost,
            labelGuidance = "⚠ Follow the product label and wear protective gear during application."
        )
    }

    // 6. Labour & Worker Cost Calculator
    fun calculateLabour(
        workType: String,
        rawArea: Double,
        unitIndex: Int,
        numWorkers: Int,
        duration: Double,
        isHourly: Boolean,
        wageRate: Double
    ): LabourResult {
        val acres = toAcres(rawArea, unitIndex)
        val totalLabourCost = numWorkers * duration * wageRate
        val costPerAcre = if (acres > 0) totalLabourCost / acres else totalLabourCost

        return LabourResult(
            workType = workType,
            areaInAcres = acres,
            numWorkers = numWorkers,
            duration = duration,
            isHourly = isHourly,
            wageRate = wageRate,
            totalLabourCost = totalLabourCost,
            costPerAcre = costPerAcre
        )
    }

    // 7. Harvesting Calculator
    fun calculateHarvesting(
        cropIndex: Int,
        rawArea: Double,
        unitIndex: Int,
        method: String, // "Combine Harvester / Machine" or "Manual Labour"
        numWorkers: Int,
        durationDays: Double,
        dailyWage: Double,
        machineRate: Double,
        isHourlyMachine: Boolean,
        hoursPerAcre: Double
    ): HarvestingResult {
        val acres = toAcres(rawArea, unitIndex)
        val crop = crops.getOrElse(cropIndex) { "Wheat" }

        val totalCost: Double
        val detailsText: String

        if (method.contains("Manual", ignoreCase = true)) {
            totalCost = numWorkers * durationDays * dailyWage
            detailsText = "$numWorkers workers for ${df(durationDays)} days @ ₹${df(dailyWage)}/day"
        } else {
            totalCost = if (isHourlyMachine) {
                acres * hoursPerAcre * machineRate
            } else {
                acres * machineRate
            }
            detailsText = if (isHourlyMachine) {
                "Machine @ ₹${df(machineRate)}/hr (${df(hoursPerAcre)} hrs/Acre)"
            } else {
                "Combine Harvester @ ₹${df(machineRate)}/Acre"
            }
        }

        val costPerAcre = if (acres > 0) totalCost / acres else totalCost

        return HarvestingResult(
            cropName = crop,
            areaInAcres = acres,
            method = method,
            totalCost = totalCost,
            costPerAcre = costPerAcre,
            detailsText = detailsText
        )
    }

    // 8. Post-Harvest, Transport & Storage Calculator
    fun calculatePostHarvest(
        cropIndex: Int,
        quantityQuintals: Double,
        distanceKm: Double = 0.0,
        transportRatePerQ: Double,
        loadingCost: Double,
        unloadingCost: Double,
        storageMonths: Double,
        storageRatePerQPerMonth: Double
    ): PostHarvestResult {
        val crop = crops.getOrElse(cropIndex) { "Wheat" }

        val distanceFactor = if (distanceKm > 0) distanceKm / 20.0 else 1.0
        val transportCost = quantityQuintals * transportRatePerQ * distanceFactor
        val storageCost = quantityQuintals * storageMonths * storageRatePerQPerMonth
        val totalCost = transportCost + loadingCost + unloadingCost + storageCost

        return PostHarvestResult(
            cropName = crop,
            quantityQuintals = quantityQuintals,
            transportCost = transportCost,
            loadingCost = loadingCost,
            unloadingCost = unloadingCost,
            storageCost = storageCost,
            totalCost = totalCost
        )
    }

    // 9. Crop Sale & Mandi Calculator
    fun calculateCropSale(
        cropIndex: Int,
        quantityQuintals: Double,
        mandiPricePerQ: Double,
        commissionPercent: Double,
        marketChargesPercent: Double,
        transportDeduction: Double,
        otherDeduction: Double
    ): CropSaleResult {
        val crop = crops.getOrElse(cropIndex) { "Soybean" }

        val grossSale = quantityQuintals * mandiPricePerQ
        val commissionDeduction = grossSale * (commissionPercent / 100.0)
        val marketChargesDeduction = grossSale * (marketChargesPercent / 100.0)
        val totalDeductions = commissionDeduction + marketChargesDeduction + transportDeduction + otherDeduction
        val netSale = grossSale - totalDeductions

        return CropSaleResult(
            cropName = crop,
            quantityQuintals = quantityQuintals,
            mandiPricePerQ = mandiPricePerQ,
            grossSale = grossSale,
            commissionDeduction = commissionDeduction,
            marketChargesDeduction = marketChargesDeduction,
            transportDeduction = transportDeduction,
            otherDeduction = otherDeduction,
            totalDeductions = totalDeductions,
            netSale = netSale
        )
    }

    // 10. Complete Crop Profit & ROI Calculator
    fun calculateCropProfit(
        cropIndex: Int,
        rawArea: Double,
        unitIndex: Int,
        expectedYieldPerAcre: Double,
        expectedPricePerQuintal: Double,
        seedCost: Double,
        machineryCost: Double,
        fertilizerCost: Double,
        irrigationCost: Double,
        sprayCost: Double,
        labourCost: Double,
        harvestingCost: Double,
        postHarvestCost: Double
    ): CropProfitResult {
        val acres = toAcres(rawArea, unitIndex)
        val crop = crops.getOrElse(cropIndex) { "Soybean" }

        val totalYieldQuintals = acres * expectedYieldPerAcre
        val expectedRevenue = totalYieldQuintals * expectedPricePerQuintal

        val totalCost = seedCost + machineryCost + fertilizerCost + irrigationCost +
                sprayCost + labourCost + harvestingCost + postHarvestCost

        val netProfit = expectedRevenue - totalCost
        val profitPerAcre = if (acres > 0) netProfit / acres else netProfit
        val breakEvenPricePerQuintal = if (totalYieldQuintals > 0) totalCost / totalYieldQuintals else 0.0
        val roiPercent = if (totalCost > 0) (netProfit / totalCost) * 100.0 else 0.0

        return CropProfitResult(
            cropName = crop,
            areaInAcres = acres,
            expectedYieldPerAcre = expectedYieldPerAcre,
            totalYieldQuintals = totalYieldQuintals,
            expectedPricePerQuintal = expectedPricePerQuintal,
            expectedRevenue = expectedRevenue,
            seedCost = seedCost,
            machineryCost = machineryCost,
            fertilizerCost = fertilizerCost,
            irrigationCost = irrigationCost,
            sprayCost = sprayCost,
            labourCost = labourCost,
            harvestingCost = harvestingCost,
            postHarvestCost = postHarvestCost,
            totalCost = totalCost,
            netProfit = netProfit,
            profitPerAcre = profitPerAcre,
            breakEvenPricePerQuintal = breakEvenPricePerQuintal,
            roiPercent = roiPercent
        )
    }
}
