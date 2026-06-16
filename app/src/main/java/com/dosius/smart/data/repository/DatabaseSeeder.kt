package com.dosius.smart.data.repository

import com.dosius.smart.data.local.dao.ExerciseCaseDao
import com.dosius.smart.data.local.dao.FoodCaseDao
import com.dosius.smart.data.local.dao.GlucoseReadingDao
import com.dosius.smart.domain.model.CarbConfidence
import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.Exercise
import com.dosius.smart.domain.model.ExerciseCase
import com.dosius.smart.domain.model.ExerciseIntensity
import com.dosius.smart.domain.model.Food
import com.dosius.smart.domain.model.FoodCase
import com.dosius.smart.domain.model.GlucoseReading
import com.dosius.smart.domain.model.GlucoseTrend
import com.dosius.smart.domain.model.InsulinType
import com.dosius.smart.domain.model.RegistrationMethod
import com.dosius.smart.domain.model.TherapyParameter
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

@Singleton
class DatabaseSeeder @Inject constructor(
    private val foodRepository: EntryRepository,
    private val exerciseRepository: ExerciseRepository,
    private val glucoseReadingDao: GlucoseReadingDao,
    private val therapyParameterRepository: TherapyParameterRepository,
    private val foodCaseDao: FoodCaseDao,
    private val exerciseCaseDao: ExerciseCaseDao
) {
    suspend fun seedIfEmpty() {
        val tz = TimeZone.currentSystemDefault()
        val nowInstant = Clock.System.now()
        val now = nowInstant.toLocalDateTime(tz)

        // Seed dates are relative to today so entries always appear in the recent past.
        // seedDays[0] = 7 days ago, seedDays[7] = today.
        val secsPerDay = 24L * 60L * 60L
        val seedDays: List<LocalDate> = (0..7).map { i ->
            Instant.fromEpochSeconds(nowInstant.epochSeconds - (7 - i).toLong() * secsPerDay)
                .toLocalDateTime(tz)
                .date
        }
        fun ld(dayIdx: Int, hour: Int, minute: Int): LocalDateTime {
            val d = seedDays[dayIdx]
            return LocalDateTime(d.year, d.monthNumber, d.dayOfMonth, hour, minute)
        }

        if (therapyParameterRepository.getCount() == 0) {
            val defaults = (0..23).flatMap { slot ->
                listOf(
                    TherapyParameter(slotIndex = slot, parameterType = "ISF",   mean = 50f, variance = 100f, nObservations = 0, lastUpdated = now, currentValue = 50f),
                    TherapyParameter(slotIndex = slot, parameterType = "ICR",   mean = 10f, variance = 4f,   nObservations = 0, lastUpdated = now, currentValue = 10f),
                    TherapyParameter(slotIndex = slot, parameterType = "BASAL", mean = 0f,  variance = 1f,   nObservations = 0, lastUpdated = now, currentValue = 0f)
                )
            }
            therapyParameterRepository.upsertAll(defaults)
        }

        if (therapyParameterRepository.getBySlotAndType(0, "TARGET_LOW") == null) {
            therapyParameterRepository.upsertAll(listOf(
                TherapyParameter(slotIndex = 0, parameterType = "TARGET_LOW",  mean = 70f,  variance = 25f, nObservations = 0, lastUpdated = now, currentValue = 70f),
                TherapyParameter(slotIndex = 0, parameterType = "TARGET_HIGH", mean = 180f, variance = 25f, nObservations = 0, lastUpdated = now, currentValue = 180f),
            ))
        }

        if (glucoseReadingDao.getLatestReading() == null) {
            glucoseReadingDao.insertAll(generateGlucoseReadings(seedDays))
        }

        if (exerciseRepository.getCount() == 0) {
            val exercises = listOf(
                Exercise("Caminar", 20f, 85),
                Exercise("Correr", 60f, 92),
                Exercise("Ciclismo", 45f, 88),
                Exercise("Natación", 55f, 78),
                Exercise("Pesas", 15f, 65),
                Exercise("Yoga", 10f, 70)
            )
            exercises.forEach { exerciseRepository.upsertExercise(it) }
        }

        val foods = listOf(
            // Cereales
            Food(id = "f01", name = "Pan blanco",       idCategory = "Cereales",  carbsPer100g = 49f, carbsRecommended = 30f, glycemicIndex = 75f, isCustom = false, isIngredient = false, entries = 0),
            Food(id = "f02", name = "Arroz blanco",     idCategory = "Cereales",  carbsPer100g = 28f, carbsRecommended = 45f, glycemicIndex = 72f, isCustom = false, isIngredient = false, entries = 0),
            Food(id = "f03", name = "Pasta (cocida)",   idCategory = "Cereales",  carbsPer100g = 25f, carbsRecommended = 60f, glycemicIndex = 55f, isCustom = false, isIngredient = false, entries = 0),
            Food(id = "f04", name = "Avena",            idCategory = "Cereales",  carbsPer100g = 66f, carbsRecommended = 40f, glycemicIndex = 55f, isCustom = false, isIngredient = true,  entries = 0),

            // Frutas
            Food(id = "f05", name = "Manzana",          idCategory = "Frutas",    carbsPer100g = 14f, carbsRecommended = 15f, glycemicIndex = 36f, isCustom = false, isIngredient = false, entries = 0),
            Food(id = "f06", name = "Plátano",          idCategory = "Frutas",    carbsPer100g = 23f, carbsRecommended = 20f, glycemicIndex = 51f, isCustom = false, isIngredient = false, entries = 0),
            Food(id = "f07", name = "Naranja",          idCategory = "Frutas",    carbsPer100g = 12f, carbsRecommended = 15f, glycemicIndex = 43f, isCustom = false, isIngredient = false, entries = 0),

            // Lácteos
            Food(id = "f08", name = "Leche entera",     idCategory = "Lácteos",   carbsPer100g = 5f,  carbsRecommended = 12f, glycemicIndex = 31f, isCustom = false, isIngredient = true,  entries = 0),
            Food(id = "f09", name = "Yogur natural",    idCategory = "Lácteos",   carbsPer100g = 6f,  carbsRecommended = 10f, glycemicIndex = 35f, isCustom = false, isIngredient = false, entries = 0),

            // Verduras
            Food(id = "f10", name = "Brócoli",          idCategory = "Verduras",  carbsPer100g = 7f,  carbsRecommended = 10f, glycemicIndex = 15f, isCustom = false, isIngredient = true,  entries = 0),
            Food(id = "f11", name = "Zanahoria",        idCategory = "Verduras",  carbsPer100g = 10f, carbsRecommended = 12f, glycemicIndex = 35f, isCustom = false, isIngredient = true,  entries = 0),
            Food(id = "f12", name = "Patata (cocida)",  idCategory = "Verduras",  carbsPer100g = 17f, carbsRecommended = 30f, glycemicIndex = 78f, isCustom = false, isIngredient = false, entries = 0),

            // Proteínas
            Food(id = "f13", name = "Pechuga de pollo", idCategory = "Proteínas", carbsPer100g = 0f,  carbsRecommended = 0f,  glycemicIndex = null, isCustom = false, isIngredient = false, entries = 0),
            Food(id = "f14", name = "Huevo",            idCategory = "Proteínas", carbsPer100g = 1f,  carbsRecommended = 1f,  glycemicIndex = null, isCustom = false, isIngredient = true,  entries = 0),
            Food(id = "f15", name = "Atún en lata",     idCategory = "Proteínas", carbsPer100g = 0f,  carbsRecommended = 0f,  glycemicIndex = null, isCustom = false, isIngredient = false, entries = 0),

            // Cereales (extended)
            Food(id = "f16", name = "Pan integral",       idCategory = "Cereales",   carbsPer100g = 43f, carbsRecommended = 30f, glycemicIndex = 58f,  isCustom = false, isIngredient = false, entries = 3, posteriorMean = 43.5f, nObservations = 3),
            Food(id = "f17", name = "Galletas María",     idCategory = "Bollería",   carbsPer100g = 74f, carbsRecommended = 20f, glycemicIndex = 70f,  isCustom = false, isIngredient = false, entries = 2, posteriorMean = 75.2f, nObservations = 2),
            Food(id = "f18", name = "Croissant",          idCategory = "Bollería",   carbsPer100g = 46f, carbsRecommended = 25f, glycemicIndex = 67f,  isCustom = false, isIngredient = false, entries = 1),
            Food(id = "f19", name = "Cereales de maíz",   idCategory = "Cereales",   carbsPer100g = 84f, carbsRecommended = 30f, glycemicIndex = 81f,  isCustom = false, isIngredient = false, entries = 2),
            Food(id = "f20", name = "Tostada de pan",     idCategory = "Cereales",   carbsPer100g = 63f, carbsRecommended = 20f, glycemicIndex = 73f,  isCustom = false, isIngredient = false, entries = 5, posteriorMean = 62.1f, nObservations = 5),

            // Frutas (extended)
            Food(id = "f21", name = "Uvas",               idCategory = "Frutas",     carbsPer100g = 17f, carbsRecommended = 17f, glycemicIndex = 46f,  isCustom = false, isIngredient = false, entries = 2),
            Food(id = "f22", name = "Sandía",             idCategory = "Frutas",     carbsPer100g = 8f,  carbsRecommended = 10f, glycemicIndex = 72f,  isCustom = false, isIngredient = false, entries = 1),
            Food(id = "f23", name = "Fresas",             idCategory = "Frutas",     carbsPer100g = 8f,  carbsRecommended = 8f,  glycemicIndex = 40f,  isCustom = false, isIngredient = false, entries = 3, posteriorMean = 7.8f, nObservations = 3),
            Food(id = "f24", name = "Mandarina",          idCategory = "Frutas",     carbsPer100g = 13f, carbsRecommended = 12f, glycemicIndex = 42f,  isCustom = false, isIngredient = false, entries = 2),

            // Lácteos (extended)
            Food(id = "f25", name = "Yogur griego",       idCategory = "Lácteos",    carbsPer100g = 4f,  carbsRecommended = 6f,  glycemicIndex = 30f,  isCustom = false, isIngredient = false, entries = 4, posteriorMean = 4.2f, nObservations = 4),
            Food(id = "f26", name = "Queso fresco",       idCategory = "Lácteos",    carbsPer100g = 3f,  carbsRecommended = 4f,  glycemicIndex = 27f,  isCustom = false, isIngredient = true,  entries = 2),
            Food(id = "f27", name = "Leche desnatada",    idCategory = "Lácteos",    carbsPer100g = 5f,  carbsRecommended = 10f, glycemicIndex = 32f,  isCustom = false, isIngredient = true,  entries = 3),

            // Legumbres
            Food(id = "f28", name = "Lentejas (cocidas)", idCategory = "Legumbres",  carbsPer100g = 20f, carbsRecommended = 30f, glycemicIndex = 32f,  isCustom = false, isIngredient = false, entries = 3, posteriorMean = 19.5f, nObservations = 3),
            Food(id = "f29", name = "Garbanzos (cocidos)",idCategory = "Legumbres",  carbsPer100g = 27f, carbsRecommended = 40f, glycemicIndex = 35f,  isCustom = false, isIngredient = false, entries = 2),
            Food(id = "f30", name = "Judías blancas",     idCategory = "Legumbres",  carbsPer100g = 22f, carbsRecommended = 35f, glycemicIndex = 40f,  isCustom = false, isIngredient = false, entries = 1),

            // Bebidas
            Food(id = "f31", name = "Zumo de naranja",    idCategory = "Bebidas",    carbsPer100g = 10f, carbsRecommended = 20f, glycemicIndex = 57f,  isCustom = false, isIngredient = false, entries = 2),
            Food(id = "f32", name = "Leche con cola-cao", idCategory = "Bebidas",    carbsPer100g = 13f, carbsRecommended = 25f, glycemicIndex = 50f,  isCustom = false, isIngredient = false, entries = 3, posteriorMean = 14.1f, nObservations = 3),
            Food(id = "f33", name = "Batido de chocolate",idCategory = "Bebidas",    carbsPer100g = 16f, carbsRecommended = 30f, glycemicIndex = 62f,  isCustom = false, isIngredient = false, entries = 1),

            // Bollería / Dulces (extended)
            Food(id = "f34", name = "Magdalena",          idCategory = "Bollería",   carbsPer100g = 55f, carbsRecommended = 22f, glycemicIndex = 65f,  isCustom = false, isIngredient = false, entries = 2),
            Food(id = "f35", name = "Bizcocho casero",    idCategory = "Bollería",   carbsPer100g = 52f, carbsRecommended = 26f, glycemicIndex = 63f,  isCustom = false, isIngredient = false, entries = 1),

            // Platos combinados (Mediterranean)
            Food(id = "f36", name = "Pizza margarita",    idCategory = "Platos",     carbsPer100g = 30f, carbsRecommended = 80f, glycemicIndex = 60f,  isCustom = false, isIngredient = false, entries = 2, posteriorMean = 31.8f, nObservations = 2),
            Food(id = "f37", name = "Tortilla española",  idCategory = "Platos",     carbsPer100g = 12f, carbsRecommended = 20f, glycemicIndex = 45f,  isCustom = false, isIngredient = false, entries = 3, posteriorMean = 11.4f, nObservations = 3),
            Food(id = "f38", name = "Paella (arroz)",     idCategory = "Platos",     carbsPer100g = 26f, carbsRecommended = 65f, glycemicIndex = 69f,  isCustom = false, isIngredient = false, entries = 2),
            Food(id = "f39", name = "Gazpacho",           idCategory = "Platos",     carbsPer100g = 5f,  carbsRecommended = 12f, glycemicIndex = 28f,  isCustom = false, isIngredient = false, entries = 2),
            Food(id = "f40", name = "Croquetas",          idCategory = "Platos",     carbsPer100g = 20f, carbsRecommended = 24f, glycemicIndex = 55f,  isCustom = false, isIngredient = false, entries = 1),

            // Verduras (extended)
            Food(id = "f41", name = "Tomate",             idCategory = "Verduras",   carbsPer100g = 4f,  carbsRecommended = 5f,  glycemicIndex = 30f,  isCustom = false, isIngredient = true,  entries = 1),
            Food(id = "f42", name = "Lechuga",            idCategory = "Verduras",   carbsPer100g = 2f,  carbsRecommended = 2f,  glycemicIndex = 15f,  isCustom = false, isIngredient = true,  entries = 0),
            Food(id = "f43", name = "Espinacas",          idCategory = "Verduras",   carbsPer100g = 4f,  carbsRecommended = 4f,  glycemicIndex = 15f,  isCustom = false, isIngredient = true,  entries = 0),
            Food(id = "f44", name = "Pimiento rojo",      idCategory = "Verduras",   carbsPer100g = 6f,  carbsRecommended = 6f,  glycemicIndex = 32f,  isCustom = false, isIngredient = true,  entries = 0),
            Food(id = "f45", name = "Cebolla",            idCategory = "Verduras",   carbsPer100g = 10f, carbsRecommended = 8f,  glycemicIndex = 15f,  isCustom = false, isIngredient = true,  entries = 0),
        )

        foods.forEach { foodRepository.upsertFood(it) }

        if (foodRepository.getEntryCount() == 0) {
        val events = listOf(
            // --- Day 0 (7 days ago, 4 entries) ---
            Entry(id = "e01", timestamp = ld(0, 7, 30),  createdAt = ld(0, 7, 30),  description = "Insulina basal",
                foodId = null, quantity = null, totalCarbs = null, carbConfidence = null, mealType = null, registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = 14f, insulinType = InsulinType.BASAL, recommendedUnits = 14f, currentGlucose = 97,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),
            Entry(id = "e02", timestamp = ld(0, 13, 45), createdAt = ld(0, 13, 45), description = "",
                foodId = "f02", quantity = 160f, totalCarbs = 45f, carbConfidence = CarbConfidence.HIGH, mealType = "Comida", registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = 5f, insulinType = InsulinType.BOLUS, recommendedUnits = 5f, currentGlucose = 103,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),
            Entry(id = "e03", timestamp = ld(0, 18, 30), createdAt = ld(0, 18, 30), description = "Paseo tarde",
                foodId = null, quantity = null, totalCarbs = null, carbConfidence = null, mealType = null, registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = 115,
                exerciseType = "Caminar", durationOfExercise = 35f, intensity = ExerciseIntensity.LOW, recommendedCarbs = null),
            Entry(id = "e04", timestamp = ld(0, 21, 0),  createdAt = ld(0, 21, 0),  description = "",
                foodId = "f13", quantity = 140f, totalCarbs = 0f, carbConfidence = CarbConfidence.CERTAIN, mealType = "Cena", registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = 108,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),

            // --- Day 1 (6 days ago, 3 entries) ---
            Entry(id = "e05", timestamp = ld(1, 10, 30), createdAt = ld(1, 10, 30), description = "Pesas en el gimnasio",
                foodId = null, quantity = null, totalCarbs = null, carbConfidence = null, mealType = null, registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = 130,
                exerciseType = "Pesas", durationOfExercise = 60f, intensity = ExerciseIntensity.HIGH, recommendedCarbs = null),
            Entry(id = "e06", timestamp = ld(1, 14, 30), createdAt = ld(1, 14, 30), description = "",
                foodId = "f03", quantity = 200f, totalCarbs = 50f, carbConfidence = CarbConfidence.HIGH, mealType = "Comida", registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = 6f, insulinType = InsulinType.BOLUS, recommendedUnits = 5f, currentGlucose = 88,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),
            Entry(id = "e07", timestamp = ld(1, 21, 0),  createdAt = ld(1, 21, 0),  description = "",
                foodId = "f09", quantity = 125f, totalCarbs = 8f, carbConfidence = CarbConfidence.HIGH, mealType = "Cena", registrationMethod = RegistrationMethod.CHAT,
                insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = 99,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),

            // --- Day 2 (5 days ago, 4 entries) ---
            Entry(id = "e08", timestamp = ld(2, 7, 30),  createdAt = ld(2, 7, 30),  description = "Insulina basal",
                foodId = null, quantity = null, totalCarbs = null, carbConfidence = null, mealType = null, registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = 14f, insulinType = InsulinType.BASAL, recommendedUnits = 14f, currentGlucose = 105,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),
            Entry(id = "e09", timestamp = ld(2, 10, 0),  createdAt = ld(2, 10, 0),  description = "",
                foodId = "f05", quantity = 150f, totalCarbs = 21f, carbConfidence = CarbConfidence.MEDIUM, mealType = "Almuerzo", registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = 152,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),
            Entry(id = "e10", timestamp = ld(2, 17, 0),  createdAt = ld(2, 17, 0),  description = "Bici por el parque",
                foodId = null, quantity = null, totalCarbs = null, carbConfidence = null, mealType = null, registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = 118,
                exerciseType = "Ciclismo", durationOfExercise = 45f, intensity = ExerciseIntensity.MEDIUM, recommendedCarbs = null),
            Entry(id = "e11", timestamp = ld(2, 20, 30), createdAt = ld(2, 20, 30), description = "",
                foodId = "f06", quantity = 100f, totalCarbs = 23f, carbConfidence = CarbConfidence.MEDIUM, mealType = "Cena", registrationMethod = RegistrationMethod.CHAT,
                insulinUnits = 3f, insulinType = InsulinType.BOLUS, recommendedUnits = 2.5f, currentGlucose = 107,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),

            // --- Day 3 (4 days ago, 2 entries) ---
            Entry(id = "e12", timestamp = ld(3, 9, 30),  createdAt = ld(3, 9, 30),  description = "Carrera mañanera",
                foodId = null, quantity = null, totalCarbs = null, carbConfidence = null, mealType = null, registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = 122,
                exerciseType = "Correr", durationOfExercise = 30f, intensity = ExerciseIntensity.HIGH, recommendedCarbs = null),
            Entry(id = "e13", timestamp = ld(3, 14, 0),  createdAt = ld(3, 14, 0),  description = "",
                foodId = "f01", quantity = 80f, totalCarbs = 39f, carbConfidence = CarbConfidence.MEDIUM, mealType = "Comida", registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = 4f, insulinType = InsulinType.BOLUS, recommendedUnits = 4f, currentGlucose = 95,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),

            // --- Day 4 (3 days ago, 1 entry) ---
            Entry(id = "e14", timestamp = ld(4, 20, 0),  createdAt = ld(4, 20, 0),  description = "",
                foodId = "f13", quantity = 150f, totalCarbs = 0f, carbConfidence = CarbConfidence.CERTAIN, mealType = "Cena", registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = 104,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),

            // --- Day 5 (2 days ago, 4 entries) ---
            Entry(id = "e15", timestamp = ld(5, 7, 30),  createdAt = ld(5, 7, 30),  description = "Insulina basal",
                foodId = null, quantity = null, totalCarbs = null, carbConfidence = null, mealType = null, registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = 14f, insulinType = InsulinType.BASAL, recommendedUnits = 14f, currentGlucose = 91,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),
            Entry(id = "e16", timestamp = ld(5, 8, 15),  createdAt = ld(5, 8, 15),  description = "",
                foodId = "f04", quantity = 60f, totalCarbs = 40f, carbConfidence = CarbConfidence.HIGH, mealType = "Desayuno", registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = 4f, insulinType = InsulinType.BOLUS, recommendedUnits = 4f, currentGlucose = 93,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),
            Entry(id = "e17", timestamp = ld(5, 11, 0),  createdAt = ld(5, 11, 0),  description = "Yoga en casa",
                foodId = null, quantity = null, totalCarbs = null, carbConfidence = null, mealType = null, registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = 116,
                exerciseType = "Yoga", durationOfExercise = 50f, intensity = ExerciseIntensity.LOW, recommendedCarbs = null),
            Entry(id = "e18", timestamp = ld(5, 14, 30), createdAt = ld(5, 14, 30), description = "",
                foodId = "f12", quantity = 180f, totalCarbs = 31f, carbConfidence = CarbConfidence.MEDIUM, mealType = "Comida", registrationMethod = RegistrationMethod.CHAT,
                insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = 87,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),

            // --- Day 6 (yesterday, 4 entries) ---
            Entry(id = "e19", timestamp = ld(6, 7, 30),  createdAt = ld(6, 7, 30),  description = "Insulina basal",
                foodId = null, quantity = null, totalCarbs = null, carbConfidence = null, mealType = null, registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = 14f, insulinType = InsulinType.BASAL, recommendedUnits = 14f, currentGlucose = 94,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),
            Entry(id = "e20", timestamp = ld(6, 8, 0),   createdAt = ld(6, 8, 0),   description = "",
                foodId = "f20", quantity = 40f, totalCarbs = 25f, carbConfidence = CarbConfidence.HIGH, mealType = "Desayuno", registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = 3f, insulinType = InsulinType.BOLUS, recommendedUnits = 2.5f, currentGlucose = 96,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),
            Entry(id = "e21", timestamp = ld(6, 14, 0),  createdAt = ld(6, 14, 0),  description = "",
                foodId = "f37", quantity = 130f, totalCarbs = 16f, carbConfidence = CarbConfidence.MEDIUM, mealType = "Comida", registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = 2f, insulinType = InsulinType.BOLUS, recommendedUnits = 2f, currentGlucose = 105,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),
            Entry(id = "e22", timestamp = ld(6, 19, 0),  createdAt = ld(6, 19, 0),  description = "Natación piscina municipal",
                foodId = null, quantity = null, totalCarbs = null, carbConfidence = null, mealType = null, registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = 132,
                exerciseType = "Natación", durationOfExercise = 40f, intensity = ExerciseIntensity.MEDIUM, recommendedCarbs = null),

            // --- Day 7 (today, 5 entries) ---
            Entry(id = "e23", timestamp = ld(7, 8, 30),  createdAt = ld(7, 8, 30),  description = "",
                foodId = "f32", quantity = 200f, totalCarbs = 26f, carbConfidence = CarbConfidence.MEDIUM, mealType = "Desayuno", registrationMethod = RegistrationMethod.CHAT,
                insulinUnits = 3f, insulinType = InsulinType.BOLUS, recommendedUnits = 3f, currentGlucose = 101,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),
            Entry(id = "e24", timestamp = ld(7, 11, 0),  createdAt = ld(7, 11, 0),  description = "",
                foodId = "f23", quantity = 100f, totalCarbs = 8f, carbConfidence = CarbConfidence.HIGH, mealType = "Almuerzo", registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = 118,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),
            Entry(id = "e25", timestamp = ld(7, 14, 30), createdAt = ld(7, 14, 30), description = "",
                foodId = "f28", quantity = 200f, totalCarbs = 40f, carbConfidence = CarbConfidence.MEDIUM, mealType = "Comida", registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = 4f, insulinType = InsulinType.BOLUS, recommendedUnits = 4.5f, currentGlucose = 92,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),
            Entry(id = "e26", timestamp = ld(7, 17, 30), createdAt = ld(7, 17, 30), description = "Carrera suave",
                foodId = null, quantity = null, totalCarbs = null, carbConfidence = null, mealType = null, registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = 110,
                exerciseType = "Correr", durationOfExercise = 25f, intensity = ExerciseIntensity.MEDIUM, recommendedCarbs = null),
            Entry(id = "e27", timestamp = ld(7, 21, 0),  createdAt = ld(7, 21, 0),  description = "",
                foodId = "f25", quantity = 150f, totalCarbs = 6f, carbConfidence = CarbConfidence.HIGH, mealType = "Cena", registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = 102,
                exerciseType = null, durationOfExercise = null, intensity = null, recommendedCarbs = null),
        )

        events.forEach { foodRepository.upsertEntry(it) }
        } // end if (getEntryCount == 0)

        if (foodCaseDao.getCount() == 0) {
            val foodCases = listOf(
                // Arroz blanco (f02) – entry e02 (day 0 lunch, 160g)
                FoodCase(id = "fc01", foodId = "f02", mealEventId = "e02", timeOfDayBucket = 2, dayOfWeek = seedDays[0].dayOfWeek.isoDayNumber,
                    quantityG = 160f, preMealBg = 103, iobAtStart = 0.4f,
                    enteredCarbsPer100g = 28f, inferredCarbsPer100g = 29.3f,
                    carbConfidence = CarbConfidence.HIGH, observedIauc = 420f, peakBg = 172, postprandialCurveJson = "[]"),
                FoodCase(id = "fc02", foodId = "f02", mealEventId = "e25", timeOfDayBucket = 2, dayOfWeek = seedDays[7].dayOfWeek.isoDayNumber,
                    quantityG = 200f, preMealBg = 92, iobAtStart = 0f,
                    enteredCarbsPer100g = 28f, inferredCarbsPer100g = 27.1f,
                    carbConfidence = CarbConfidence.MEDIUM, observedIauc = 510f, peakBg = 168, postprandialCurveJson = "[]"),

                // Pasta (f03) – entry e06 (day 1 lunch, 200g)
                FoodCase(id = "fc03", foodId = "f03", mealEventId = "e06", timeOfDayBucket = 2, dayOfWeek = seedDays[1].dayOfWeek.isoDayNumber,
                    quantityG = 200f, preMealBg = 88, iobAtStart = 1.8f,
                    enteredCarbsPer100g = 25f, inferredCarbsPer100g = 26.5f,
                    carbConfidence = CarbConfidence.HIGH, observedIauc = 380f, peakBg = 155, postprandialCurveJson = "[]"),

                // Yogur natural (f09) – entry e07 (day 1 dinner, 125g)
                FoodCase(id = "fc04", foodId = "f09", mealEventId = "e07", timeOfDayBucket = 3, dayOfWeek = seedDays[1].dayOfWeek.isoDayNumber,
                    quantityG = 125f, preMealBg = 99, iobAtStart = 0.2f,
                    enteredCarbsPer100g = 6f, inferredCarbsPer100g = 5.8f,
                    carbConfidence = CarbConfidence.HIGH, observedIauc = 95f, peakBg = 124, postprandialCurveJson = "[]"),

                // Manzana (f05) – entry e09 (day 2 snack, 150g)
                FoodCase(id = "fc05", foodId = "f05", mealEventId = "e09", timeOfDayBucket = 1, dayOfWeek = seedDays[2].dayOfWeek.isoDayNumber,
                    quantityG = 150f, preMealBg = 152, iobAtStart = 0.3f,
                    enteredCarbsPer100g = 14f, inferredCarbsPer100g = 13.2f,
                    carbConfidence = CarbConfidence.MEDIUM, observedIauc = 145f, peakBg = 188, postprandialCurveJson = "[]"),

                // Plátano (f06) – entry e11 (day 2 dinner, 100g)
                FoodCase(id = "fc06", foodId = "f06", mealEventId = "e11", timeOfDayBucket = 3, dayOfWeek = seedDays[2].dayOfWeek.isoDayNumber,
                    quantityG = 100f, preMealBg = 107, iobAtStart = 0.6f,
                    enteredCarbsPer100g = 23f, inferredCarbsPer100g = 24.1f,
                    carbConfidence = CarbConfidence.MEDIUM, observedIauc = 220f, peakBg = 148, postprandialCurveJson = "[]"),

                // Pan blanco (f01) – entry e13 (day 3 lunch, 80g)
                FoodCase(id = "fc07", foodId = "f01", mealEventId = "e13", timeOfDayBucket = 2, dayOfWeek = seedDays[3].dayOfWeek.isoDayNumber,
                    quantityG = 80f, preMealBg = 95, iobAtStart = 0f,
                    enteredCarbsPer100g = 49f, inferredCarbsPer100g = 51.2f,
                    carbConfidence = CarbConfidence.MEDIUM, observedIauc = 310f, peakBg = 162, postprandialCurveJson = "[]"),

                // Avena (f04) – entry e16 (day 5 breakfast, 60g)
                FoodCase(id = "fc08", foodId = "f04", mealEventId = "e16", timeOfDayBucket = 1, dayOfWeek = seedDays[5].dayOfWeek.isoDayNumber,
                    quantityG = 60f, preMealBg = 93, iobAtStart = 0.1f,
                    enteredCarbsPer100g = 66f, inferredCarbsPer100g = 65.3f,
                    carbConfidence = CarbConfidence.HIGH, observedIauc = 290f, peakBg = 154, postprandialCurveJson = "[]"),

                // Patata cocida (f12) – entry e18 (day 5 lunch, 180g)
                FoodCase(id = "fc09", foodId = "f12", mealEventId = "e18", timeOfDayBucket = 2, dayOfWeek = seedDays[5].dayOfWeek.isoDayNumber,
                    quantityG = 180f, preMealBg = 87, iobAtStart = 0f,
                    enteredCarbsPer100g = 17f, inferredCarbsPer100g = 18.4f,
                    carbConfidence = CarbConfidence.MEDIUM, observedIauc = 260f, peakBg = 145, postprandialCurveJson = "[]"),

                // Tostada de pan (f20) – entry e20 (day 6 breakfast, 40g)
                FoodCase(id = "fc10", foodId = "f20", mealEventId = "e20", timeOfDayBucket = 1, dayOfWeek = seedDays[6].dayOfWeek.isoDayNumber,
                    quantityG = 40f, preMealBg = 96, iobAtStart = 0.2f,
                    enteredCarbsPer100g = 63f, inferredCarbsPer100g = 64.8f,
                    carbConfidence = CarbConfidence.HIGH, observedIauc = 195f, peakBg = 147, postprandialCurveJson = "[]"),

                // Tortilla española (f37) – entry e21 (day 6 lunch, 130g)
                FoodCase(id = "fc11", foodId = "f37", mealEventId = "e21", timeOfDayBucket = 2, dayOfWeek = seedDays[6].dayOfWeek.isoDayNumber,
                    quantityG = 130f, preMealBg = 105, iobAtStart = 0.5f,
                    enteredCarbsPer100g = 12f, inferredCarbsPer100g = 11.2f,
                    carbConfidence = CarbConfidence.MEDIUM, observedIauc = 120f, peakBg = 131, postprandialCurveJson = "[]"),

                // Leche con cola-cao (f32) – entry e23 (today breakfast, 200g)
                FoodCase(id = "fc12", foodId = "f32", mealEventId = "e23", timeOfDayBucket = 1, dayOfWeek = seedDays[7].dayOfWeek.isoDayNumber,
                    quantityG = 200f, preMealBg = 101, iobAtStart = 0.3f,
                    enteredCarbsPer100g = 13f, inferredCarbsPer100g = 14.5f,
                    carbConfidence = CarbConfidence.MEDIUM, observedIauc = 230f, peakBg = 153, postprandialCurveJson = "[]"),

                // Fresas (f23) – entry e24 (today snack, 100g)
                FoodCase(id = "fc13", foodId = "f23", mealEventId = "e24", timeOfDayBucket = 1, dayOfWeek = seedDays[7].dayOfWeek.isoDayNumber,
                    quantityG = 100f, preMealBg = 118, iobAtStart = 0.8f,
                    enteredCarbsPer100g = 8f, inferredCarbsPer100g = 7.6f,
                    carbConfidence = CarbConfidence.HIGH, observedIauc = 75f, peakBg = 132, postprandialCurveJson = "[]"),

                // Lentejas (f28) – entry e25 (today lunch, 200g)
                FoodCase(id = "fc14", foodId = "f28", mealEventId = "e25", timeOfDayBucket = 2, dayOfWeek = seedDays[7].dayOfWeek.isoDayNumber,
                    quantityG = 200f, preMealBg = 92, iobAtStart = 0f,
                    enteredCarbsPer100g = 20f, inferredCarbsPer100g = 19.2f,
                    carbConfidence = CarbConfidence.MEDIUM, observedIauc = 300f, peakBg = 147, postprandialCurveJson = "[]"),

                // Yogur griego (f25) – entry e27 (today dinner, 150g)
                FoodCase(id = "fc15", foodId = "f25", mealEventId = "e27", timeOfDayBucket = 3, dayOfWeek = seedDays[7].dayOfWeek.isoDayNumber,
                    quantityG = 150f, preMealBg = 102, iobAtStart = 0.1f,
                    enteredCarbsPer100g = 4f, inferredCarbsPer100g = 4.3f,
                    carbConfidence = CarbConfidence.HIGH, observedIauc = 62f, peakBg = 118, postprandialCurveJson = "[]"),
            )
            foodCases.forEach { foodCaseDao.insert(it) }
        } // end if (foodCaseDao.getCount() == 0)

        if (exerciseCaseDao.getCount() == 0) {
            val exerciseCases = listOf(
                ExerciseCase(id = "ec01", exerciseType = "Caminar",  timestamp = ld(0, 18, 30), preExerciseBg = 115, iobAtStart = 0.1f, durationMinutes = 35f, intensity = "LOW",    expectedDrop = 12, actualDrop = 10, observedIauc = 85f),
                ExerciseCase(id = "ec02", exerciseType = "Pesas",    timestamp = ld(1, 10, 30), preExerciseBg = 130, iobAtStart = 0.2f, durationMinutes = 60f, intensity = "HIGH",   expectedDrop = 15, actualDrop = 18, observedIauc = 150f),
                ExerciseCase(id = "ec03", exerciseType = "Ciclismo", timestamp = ld(2, 17,  0), preExerciseBg = 118, iobAtStart = 0.0f, durationMinutes = 45f, intensity = "MEDIUM", expectedDrop = 34, actualDrop = 28, observedIauc = 230f),
                ExerciseCase(id = "ec04", exerciseType = "Correr",   timestamp = ld(3,  9, 30), preExerciseBg = 122, iobAtStart = 0.0f, durationMinutes = 30f, intensity = "HIGH",   expectedDrop = 30, actualDrop = 35, observedIauc = 290f),
                ExerciseCase(id = "ec05", exerciseType = "Yoga",     timestamp = ld(5, 11,  0), preExerciseBg = 116, iobAtStart = 0.0f, durationMinutes = 50f, intensity = "LOW",    expectedDrop =  8, actualDrop =  6, observedIauc = 50f),
                ExerciseCase(id = "ec06", exerciseType = "Natación", timestamp = ld(6, 19,  0), preExerciseBg = 132, iobAtStart = 0.0f, durationMinutes = 40f, intensity = "MEDIUM", expectedDrop = 37, actualDrop = 32, observedIauc = 260f),
                ExerciseCase(id = "ec07", exerciseType = "Correr",   timestamp = ld(7, 17, 30), preExerciseBg = 110, iobAtStart = 0.0f, durationMinutes = 25f, intensity = "MEDIUM", expectedDrop = 25, actualDrop = 22, observedIauc = 185f),
            )
            exerciseCases.forEach { exerciseCaseDao.insert(it) }
        } // end if (exerciseCaseDao.getCount() == 0)
    }

    // ── Glucose seeding ──────────────────────────────────────────────────────────
    //
    // Generates 7 days of CGM readings at 15-minute intervals.
    // Pattern: realistic diabetic curve with meal spikes, dawn phenomenon,
    // and exercise dips matching the seeded Entry events.
    // seedDays[1]..seedDays[7] map to glucose days 0..6.

    private fun generateGlucoseReadings(seedDays: List<LocalDate>): List<GlucoseReading> {
        // Base profile: minute-of-day → mg/dL
        val baseProfile = listOf(
            0    to 85,
            90   to 82,
            270  to 78,   // overnight low ~4:30
            390  to 88,   // dawn phenomenon
            450  to 100,  // waking
            510  to 108,  // rising pre-breakfast
            570  to 158,  // post-breakfast peak (~9:30)
            660  to 118,  // coming down
            750  to 96,   // pre-lunch
            870  to 175,  // post-lunch peak (~2:30)
            960  to 122,  // coming down
            1080 to 98,   // pre-dinner
            1230 to 164,  // post-dinner peak (~8:30 pm)
            1350 to 106,  // coming down
            1440 to 88    // midnight (end anchor)
        )

        // Per-day glucose offset to add variety across the week
        val dayOffsets = listOf(0, 5, -5, 12, -8, 4, -2)

        // Exercise dips per day: list of (startMin, endMin, dipMgDl)
        // Aligned with the Entry events seeded above
        val exerciseDips = mapOf(
            0 to listOf(Triple(630, 720, -25)),   // day 1 (6 days ago): gym 10:30, HIGH
            1 to listOf(Triple(1020, 1080, -15)), // day 2 (5 days ago): bike 17:00, MEDIUM
            2 to listOf(Triple(570, 630, -22)),   // day 3 (4 days ago): run 9:30, HIGH
            4 to listOf(Triple(660, 720, -8))     // day 5 (2 days ago): yoga 11:00, LOW
        )

        val readings = mutableListOf<GlucoseReading>()

        for (dayIndex in 0..6) {
            val d = seedDays[dayIndex + 1]  // seedDays[1]=6 days ago .. seedDays[7]=today
            val dayOffset = dayOffsets[dayIndex]
            val dips = exerciseDips[dayIndex] ?: emptyList()
            var prevGlucose: Int? = null

            for (step in 0 until 96) {  // 96 × 15 min = 24 h
                val minute = step * 15

                // 1. Base curve via linear interpolation
                var glucose = interpolate(baseProfile, minute) + dayOffset

                // 2. Exercise dip: triangle shape over [start, end]
                for ((start, end, dip) in dips) {
                    if (minute in start..end) {
                        val t = (minute - start).toFloat() / (end - start)
                        val shape = if (t < 0.5f) t * 2f else (1f - t) * 2f
                        glucose += (dip * shape).toInt()
                    }
                }

                // 3. Subtle pseudo-noise so readings don't look perfectly smooth
                glucose += (sin((dayIndex * 137 + step) * 0.4) * 4).toInt()
                glucose = glucose.coerceIn(55, 250)

                // 4. Trend rate: mg/dL per minute vs previous reading
                val trendRate = if (prevGlucose != null) {
                    (glucose - prevGlucose).toFloat() / 15f
                } else 0f

                val hour = minute / 60
                val min  = minute % 60
                val hh = hour.toString().padStart(2, '0')
                val mm = min.toString().padStart(2, '0')
                val yy = d.year
                val mo = d.monthNumber.toString().padStart(2, '0')
                val dd = d.dayOfMonth.toString().padStart(2, '0')

                readings.add(
                    GlucoseReading(
                        id           = "g_${yy}${mo}${dd}_$hh$mm",
                        timestamp    = LocalDateTime(d.year, d.monthNumber, d.dayOfMonth, hour, min),
                        glucoseValue = glucose,
                        trend        = GlucoseTrend.fromRate(trendRate),
                        trendRate    = trendRate,
                        source       = "SEED"
                    )
                )
                prevGlucose = glucose
            }
        }

        return readings
    }

    /** Linear interpolation through a sorted list of (x, y) key-points. */
    private fun interpolate(points: List<Pair<Int, Int>>, x: Int): Int {
        val lo = points.lastOrNull { it.first <= x } ?: return points.first().second
        val hi = points.firstOrNull { it.first > x }  ?: return points.last().second
        val t = (x - lo.first).toFloat() / (hi.first - lo.first)
        return (lo.second + t * (hi.second - lo.second)).toInt()
    }
}
