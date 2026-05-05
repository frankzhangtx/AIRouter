package com.example.cctest.routing.model

import android.os.Bundle

data class RecordRef(
    val recordId: String? = null,
    val displayName: String? = null,
    val phone: String? = null,
    val city: String? = null
) {
    fun toBundle(prefix: String = "record_ref"): Bundle {
        return Bundle().apply {
            putString("${prefix}_record_id", recordId)
            putString("${prefix}_display_name", displayName)
            putString("${prefix}_phone", phone)
            putString("${prefix}_city", city)
        }
    }

    companion object {
        fun fromBundle(bundle: Bundle, prefix: String = "record_ref"): RecordRef {
            return RecordRef(
                recordId = bundle.getString("${prefix}_record_id"),
                displayName = bundle.getString("${prefix}_display_name"),
                phone = bundle.getString("${prefix}_phone"),
                city = bundle.getString("${prefix}_city")
            )
        }
    }
}
