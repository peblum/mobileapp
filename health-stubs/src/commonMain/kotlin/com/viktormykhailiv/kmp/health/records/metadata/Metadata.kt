package com.viktormykhailiv.kmp.health.records.metadata

data class Metadata(
    val id: String,
    val device: Device,
) {
    companion object {
        fun autoRecorded(
            id: String,
            device: Device,
        ): Metadata = Metadata(id = id, device = device)
    }
}

data class Device(
    val type: DeviceType,
)

enum class DeviceType {
    Watch,
}
