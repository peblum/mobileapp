package com.viktormykhailiv.kmp.health.records

import com.viktormykhailiv.kmp.health.HealthRecord
import com.viktormykhailiv.kmp.health.records.metadata.Metadata
import kotlin.time.Instant

data class StepsRecord(
    val startTime: Instant,
    val endTime: Instant,
    val count: Int,
    val metadata: Metadata,
) : HealthRecord

data class HeartRateRecord(
    val startTime: Instant,
    val endTime: Instant,
    val samples: List<Sample>,
    val metadata: Metadata,
) : HealthRecord {
    data class Sample(
        val time: Instant,
        val beatsPerMinute: Int,
    )
}

data class SleepSessionRecord(
    val startTime: Instant,
    val endTime: Instant,
    val stages: List<Stage>,
    val metadata: Metadata,
) : HealthRecord {
    data class Stage(
        val startTime: Instant,
        val endTime: Instant,
        val type: SleepStageType,
    )
}

data class ExerciseSessionRecord(
    val startTime: Instant,
    val endTime: Instant,
    val exerciseType: ExerciseType,
    val title: String?,
    val exerciseRoute: Any?,
    val metadata: Metadata,
) : HealthRecord

enum class ExerciseType {
    Walking,
    Running,
    OtherWorkout,
}

enum class SleepStageType {
    Deep,
    Light,
}
