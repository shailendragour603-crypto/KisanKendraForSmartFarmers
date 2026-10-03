package shop.sabjiwala.kisankendra.utils

data class FarmerScheme(
    val id: String,
    val title: String,
    val state: String,
    val benefitAmount: String,
    val overview: String,
    val eligibility: String,
    val requiredDocuments: String,
    val officialPortalUrl: String,
    val relatedArticleKeyword: String
)

object SchemeUtils {

    val statesList = listOf(
        "All States",
        "Central (All India)",
        "Uttar Pradesh",
        "Madhya Pradesh",
        "Telangana",
        "Odisha",
        "Andhra Pradesh",
        "Jharkhand",
        "Bihar",
        "Gujarat",
        "Chhattisgarh"
    )

    val schemesList = listOf(
        // Central Schemes
        FarmerScheme(
            id = "s_pmkisan",
            title = "PM-Kisan Samman Nidhi Yojana",
            state = "Central (All India)",
            benefitAmount = "₹6,000 / year direct bank transfer (3 installments of ₹2,000)",
            overview = "Central sector scheme providing income support to all landholding farmer families across India to supplement their financial needs for procuring various inputs.",
            eligibility = "Small & marginal landholding farmer families with cultivated land in their name.",
            requiredDocuments = "Aadhaar Card, Land Ownership Khatauni, Aadhaar-linked Bank Account, Farmer Registry ID.",
            officialPortalUrl = "https://pmkisan.gov.in",
            relatedArticleKeyword = "PM Kisan Samman Nidhi: Step-by-Step Guide for Farmers to Register"
        ),
        FarmerScheme(
            id = "s_pmfby",
            title = "Pradhan Mantri Fasal Bima Yojana (PMFBY)",
            state = "Central (All India)",
            benefitAmount = "Comprehensive crop insurance cover against yield loss due to non-preventable risks.",
            overview = "Provides financial support to farmers suffering crop loss/damage arising out of unseasonal rains, drought, flood, pests, and natural calamities.",
            eligibility = "All farmers including sharecroppers and tenant farmers growing notified crops in notified areas.",
            requiredDocuments = "Aadhaar Card, Land Records (Khasra/Khatauni), Sowing Certificate, Bank Passbook.",
            officialPortalUrl = "https://pmfby.gov.in",
            relatedArticleKeyword = "Pradhan Mantri Fasal Bima Yojana: Securing India's Farmers Against Crop Loss"
        ),
        FarmerScheme(
            id = "s_kcc",
            title = "Kisan Credit Card (KCC) Scheme",
            state = "Central (All India)",
            benefitAmount = "Concessional credit up to ₹3 Lakhs at 4% effective annual interest rate.",
            overview = "Provides adequate and timely credit support from the banking system under a single window with flexible and simplified procedure.",
            eligibility = "All farmers, individual/joint borrowers, tenant farmers, self-help groups, and animal husbandry/fisheries farmers.",
            requiredDocuments = "KCC Application Form, Aadhaar Card, Land Record Copy, Passport Size Photos.",
            officialPortalUrl = "https://myscheme.gov.in/schemes/kcc",
            relatedArticleKeyword = "Kisan Credit Card (KCC) Scheme: Avail Low-Interest Loans"
        ),
        FarmerScheme(
            id = "s_smam",
            title = "Sub-Mission on Agricultural Mechanization (SMAM)",
            state = "Central (All India)",
            benefitAmount = "50% to 80% subsidy on Tractors, Rotavators, Drones & Harvesters.",
            overview = "Promotes farm mechanization among small and marginal farmers and establishes Custom Hiring Centers (CHC).",
            eligibility = "All farmers registered on the agrimachinery portal.",
            requiredDocuments = "Aadhaar Card, Land Khatauni, Bank Passbook, Tractor RC (if implement subsidy).",
            officialPortalUrl = "https://agrimachinery.nic.in",
            relatedArticleKeyword = "Sub-Mission on Agricultural Mechanization (SMAM)"
        ),
        FarmerScheme(
            id = "s_drip",
            title = "Per Drop More Crop - Drip & Sprinkler Subsidy",
            state = "Central (All India)",
            benefitAmount = "55% subsidy for Small & Marginal Farmers, 45% for other farmers.",
            overview = "Focuses on enhancing water use efficiency at farm level through Micro Irrigation technologies like Drip and Sprinkler systems.",
            eligibility = "Farmers having cultivated land with assured irrigation water source.",
            requiredDocuments = "Aadhaar Card, Land Revenue Records, Soil & Water Testing Report, Quotation from vendor.",
            officialPortalUrl = "https://pmksy.gov.in",
            relatedArticleKeyword = "Boost Your Crop Yields with Less Water"
        ),
        FarmerScheme(
            id = "s_pmkusum",
            title = "PM KUSUM Solar Pump Scheme",
            state = "Central (All India)",
            benefitAmount = "Up to 90% subsidy for installing off-grid solar water pumps.",
            overview = "Helps farmers replace diesel irrigation pumps with clean solar energy pumps and sell surplus solar power back to the grid.",
            eligibility = "Farmers, Panchayats, and Cooperatives with agricultural land.",
            requiredDocuments = "Aadhaar Card, Land Ownership Copy, Electricity Connection Details, Bank Passbook.",
            officialPortalUrl = "https://pmkusum.mnre.gov.in",
            relatedArticleKeyword = "PM-KUSUM Scheme: A Farmer’s Guide to Solar Power, Irrigation, and Extra Income"
        ),

        // Uttar Pradesh Schemes
        FarmerScheme(
            id = "s_up_minikit",
            title = "UP Free Pulse & Oilseed Seed Minikit Scheme",
            state = "Uttar Pradesh",
            benefitAmount = "100% Free Seed Minikits (Mustard 2kg, Gram/Pea 8kg).",
            overview = "UP Government scheme providing free improved seed minikits to small and marginal farmers to boost Rabi pulse and oilseed yield.",
            eligibility = "Farmers registered on UP Agriculture Department Portal.",
            requiredDocuments = "Aadhaar Card, Khatauni Land Record, Farmer Registry Number.",
            officialPortalUrl = "https://agriculture.up.gov.in",
            relatedArticleKeyword = "UP Government Offering Free Pulse and Oilseed Seed Minikits: How to Apply"
        ),
        FarmerScheme(
            id = "s_up_durghatna",
            title = "UP Mukhyamantri Krishak Durghatna Kalyan Yojana",
            state = "Uttar Pradesh",
            benefitAmount = "Financial assistance up to ₹5 Lakhs in case of accidental death or disability.",
            overview = "Social security umbrella scheme for UP farmers and farm laborers between age 18 to 70 years.",
            eligibility = "All account-holder farmers and landless agricultural laborers in UP.",
            requiredDocuments = "Aadhaar Card, Death/Disability Certificate, Khatauni, Bank Passbook.",
            officialPortalUrl = "https://up.gov.in",
            relatedArticleKeyword = "UP Mukhyamantri Krishak Durghatna Kalyan Yojana"
        ),

        // Madhya Pradesh Schemes
        FarmerScheme(
            id = "s_mp_kalyan",
            title = "MP Mukhyamantri Kisan Kalyan Yojana",
            state = "Madhya Pradesh",
            benefitAmount = "₹6,000 / year state top-up (Total ₹12,000 / year with PM-Kisan).",
            overview = "MP state government scheme transferring additional financial assistance directly into beneficiary bank accounts in two equal installments.",
            eligibility = "All PM-Kisan beneficiary farmers resident in Madhya Pradesh.",
            requiredDocuments = "PM-Kisan ID, Aadhaar Card, MP Samagra ID, Bank Account.",
            officialPortalUrl = "https://saara.mp.gov.in",
            relatedArticleKeyword = "Madhya Pradesh"
        ),
        FarmerScheme(
            id = "s_mp_bhavantar",
            title = "MP Bhavantar Bhugtan Yojana",
            state = "Madhya Pradesh",
            benefitAmount = "Price deficit compensation if Mandi selling price falls below Government MSP.",
            overview = "Protects oilseed and pulse farmers in Madhya Pradesh from distress sales during market price crashes.",
            eligibility = "Farmers registered on MP e-Uparjan portal selling notified crops in APMC Mandis.",
            requiredDocuments = "e-Uparjan Registration Copy, Mandi Sale Receipt, Aadhaar, Bank Account.",
            officialPortalUrl = "https://mpeuparjan.nic.in",
            relatedArticleKeyword = "Understanding the Mukhyamantri Bhavantar Bhugtan Yojana"
        ),

        // Telangana Schemes
        FarmerScheme(
            id = "s_ts_rythubandhu",
            title = "Rythu Bandhu Scheme",
            state = "Telangana",
            benefitAmount = "₹10,000 / acre / year direct crop investment support.",
            overview = "Telangana flagship scheme providing cash support per acre every season to buy seeds, fertilizers, and pesticides.",
            eligibility = "Pattadar landowning farmers in Telangana state.",
            requiredDocuments = "Pattadar Passbook, Aadhaar Card, Bank Passbook.",
            officialPortalUrl = "https://rythubandhu.telangana.gov.in",
            relatedArticleKeyword = "Rythu Bandhu"
        ),

        // Odisha Schemes
        FarmerScheme(
            id = "s_od_kalia",
            title = "KALIA Scheme (Krushak Assistance for Livelihood and Income Augmentation)",
            state = "Odisha",
            benefitAmount = "₹10,000 / year financial assistance for small farmers & landless laborers.",
            overview = "Comprehensive financial package for Odisha farmers providing support for cultivation, livelihood, and life insurance.",
            eligibility = "Small and marginal farmers, landless agricultural households, and vulnerable agricultural families in Odisha.",
            requiredDocuments = "Aadhaar Card, Ration Card, Bank Passbook, Land Document.",
            officialPortalUrl = "https://kalia.odisha.gov.in",
            relatedArticleKeyword = "KALIA"
        ),

        // Andhra Pradesh Schemes
        FarmerScheme(
            id = "s_ap_rythubharosa",
            title = "YSR Rythu Bharosa",
            state = "Andhra Pradesh",
            benefitAmount = "₹13,500 / year financial assistance to farmer families including tenant farmers.",
            overview = "Investment support scheme paid in three installments during May, October, and January ahead of crop seasons.",
            eligibility = "Farmer families owning cultivated land and tenant farmers belonging to SC/ST/BC/Minority categories.",
            requiredDocuments = "Aadhaar Card, Land Adangal Copy / CCRC Card for Tenants, Bank Passbook.",
            officialPortalUrl = "https://rythubharosa.ap.gov.in",
            relatedArticleKeyword = "Rythu Bharosa"
        ),

        // Bihar Schemes
        FarmerScheme(
            id = "s_br_krishiinput",
            title = "Bihar Krishi Input Subsidy Scheme",
            state = "Bihar",
            benefitAmount = "Up to ₹13,500 / hectare for rainfed crop damage, ₹18,000 / hectare for irrigated crop loss.",
            overview = "Financial grant to compensate farmers in Bihar who suffered crop loss due to heavy rainfall, floods, or drought.",
            eligibility = "Farmers residing in flood or drought-notified blocks of Bihar.",
            requiredDocuments = "DBT Agriculture Registration Number, Aadhaar, LPC Land Possession Certificate, Bank Details.",
            officialPortalUrl = "https://dbtagriculture.bihar.gov.in",
            relatedArticleKeyword = "Bihar Krishi Input Anudan Yojana 2026-27: Apply Now for Crop Loss Compensation"
        ),

        // Gujarat Schemes
        FarmerScheme(
            id = "s_gj_kisansahay",
            title = "Chief Minister Kisan Sahay Yojana",
            state = "Gujarat",
            benefitAmount = "Up to ₹25,000 / hectare compensation for 33% to 60% crop damage.",
            overview = "Zero-premium crop assistance scheme replacing crop insurance in Gujarat to cover drought, heavy rain, and unseasonal rain.",
            eligibility = "All landholding farmers registered in 8-A and 7/12 land records of Gujarat.",
            requiredDocuments = "Aadhaar Card, 7/12 and 8-A Land Record Copy, Bank Passbook.",
            officialPortalUrl = "https://ikhedut.gujarat.gov.in",
            relatedArticleKeyword = "Gujarat Mukhya Mantri Kisan Sahay Yojana"
        )
    )
}
