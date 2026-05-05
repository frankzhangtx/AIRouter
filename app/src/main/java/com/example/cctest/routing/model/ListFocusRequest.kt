package com.example.cctest.routing.model

import android.os.Bundle

data class ListFocusRequest(
    val recordId: String? = null,
    val position: Int? = null,
    val queryName: String? = null,
    val queryPhone: String? = null,
    val queryCity: String? = null,
    val matchMode: String = MATCH_MODE_NONE,
    val autoOpenDetail: Boolean = false,
    val candidateCount: Int = 0
) {
    fun toBundle(): Bundle {
        return Bundle().apply {
            putString(KEY_RECORD_ID, recordId)
            putInt(KEY_POSITION, position ?: -1)
            putString(KEY_QUERY_NAME, queryName)
            putString(KEY_QUERY_PHONE, queryPhone)
            putString(KEY_QUERY_CITY, queryCity)
            putString(KEY_MATCH_MODE, matchMode)
            putBoolean(KEY_AUTO_OPEN_DETAIL, autoOpenDetail)
            putInt(KEY_CANDIDATE_COUNT, candidateCount)
        }
    }

    fun hasLocator(): Boolean {
        return recordId != null ||
            position != null ||
            !queryName.isNullOrBlank() ||
            !queryPhone.isNullOrBlank() ||
            !queryCity.isNullOrBlank()
    }

    fun summaryLabel(): String {
        return when {
            recordId != null -> "记录ID定位"
            position != null -> "第 $position 条定位"
            !queryName.isNullOrBlank() -> "姓名定位：$queryName"
            !queryPhone.isNullOrBlank() -> "手机号定位：$queryPhone"
            !queryCity.isNullOrBlank() -> "城市定位：$queryCity"
            else -> "列表定位"
        }
    }

    companion object {
        const val KEY_RECORD_ID = "list_focus_record_id"
        const val KEY_POSITION = "list_focus_position"
        const val KEY_QUERY_NAME = "list_focus_query_name"
        const val KEY_QUERY_PHONE = "list_focus_query_phone"
        const val KEY_QUERY_CITY = "list_focus_query_city"
        const val KEY_MATCH_MODE = "list_focus_match_mode"
        const val KEY_AUTO_OPEN_DETAIL = "list_focus_auto_open_detail"
        const val KEY_CANDIDATE_COUNT = "list_focus_candidate_count"

        const val MATCH_MODE_NONE = "NONE"
        const val MATCH_MODE_RECORD_ID = "RECORD_ID"
        const val MATCH_MODE_POSITION = "POSITION"
        const val MATCH_MODE_PHONE = "PHONE"
        const val MATCH_MODE_NAME = "NAME"
        const val MATCH_MODE_CITY = "CITY"
        const val MATCH_MODE_AMBIGUOUS = "AMBIGUOUS"

        fun fromBundle(bundle: Bundle?): ListFocusRequest? {
            if (bundle == null) {
                return null
            }
            val recordId = bundle.getString(KEY_RECORD_ID)
            val position = bundle.getInt(KEY_POSITION, -1).takeIf { it > 0 }
            val queryName = bundle.getString(KEY_QUERY_NAME)
            val queryPhone = bundle.getString(KEY_QUERY_PHONE)
            val queryCity = bundle.getString(KEY_QUERY_CITY)
            val matchMode = bundle.getString(KEY_MATCH_MODE).orEmpty().ifBlank { MATCH_MODE_NONE }
            val autoOpenDetail = bundle.getBoolean(KEY_AUTO_OPEN_DETAIL, false)
            val candidateCount = bundle.getInt(KEY_CANDIDATE_COUNT, 0)
            val request = ListFocusRequest(
                recordId = recordId,
                position = position,
                queryName = queryName,
                queryPhone = queryPhone,
                queryCity = queryCity,
                matchMode = matchMode,
                autoOpenDetail = autoOpenDetail,
                candidateCount = candidateCount
            )
            if (!request.hasLocator()) {
                return null
            }
            return request
        }
    }
}
