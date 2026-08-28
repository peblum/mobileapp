package com.viktormykhailiv.kmp.health

sealed class HealthDataType {
    data object Steps : HealthDataType()
    data object HeartRate : HealthDataType()
    data object Sleep : HealthDataType()

    data class Exercise(
        val activeEnergyBurned: Boolean = false,
        val cyclingPower: Boolean = false,
        val cyclingSpeed: Boolean = false,
        val flightsClimbed: Boolean = false,
        val distanceWalkingRunning: Boolean = false,
        val runningSpeed: Boolean = false,
    ) : HealthDataType()
}

interface HealthRecord

class HealthManager {
    fun isAvailable(): Result<Boolean> = Result.success(false)

    suspend fun requestAuthorization(
        readTypes: List<HealthDataType>,
        writeTypes: List<HealthDataType>,
    ): Result<Boolean> = Result.success(false)

    suspend fun isAuthorized(
        readTypes: List<HealthDataType>,
        writeTypes: List<HealthDataType>,
    ): Result<Boolean> = Result.success(false)

    suspend fun writeData(records: List<HealthRecord>): Result<Unit> = Result.success(Unit)
}

class HealthManagerFactory {
    fun createManager(): HealthManager = HealthManager()
}
