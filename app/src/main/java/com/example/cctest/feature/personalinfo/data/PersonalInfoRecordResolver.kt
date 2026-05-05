package com.example.cctest.feature.personalinfo.data

import com.example.cctest.routing.model.ListFocusRequest
import com.example.cctest.routing.model.RecordRef
import com.example.cctest.routing.parser.ParseSlots

class PersonalInfoRecordResolver(
    private val repository: PersonalInfoRepository
) {
    fun resolveFromRecordRef(recordRef: RecordRef): RecordResolution {
        return resolve(
            recordId = recordRef.recordId,
            position = null,
            queryName = recordRef.displayName,
            queryPhone = recordRef.phone,
            queryCity = recordRef.city
        )
    }

    fun resolveFromSlots(slots: ParseSlots): RecordResolution {
        return resolve(
            recordId = null,
            position = slots.listPosition,
            queryName = slots.personName,
            queryPhone = slots.phone,
            queryCity = slots.city
        )
    }

    fun resolveFromFocusRequest(request: ListFocusRequest): RecordResolution {
        return resolve(
            recordId = request.recordId,
            position = request.position,
            queryName = request.queryName,
            queryPhone = request.queryPhone,
            queryCity = request.queryCity
        )
    }

    private fun resolve(
        recordId: String?,
        position: Int?,
        queryName: String?,
        queryPhone: String?,
        queryCity: String?
    ): RecordResolution {
        val records = repository.getRecords()

        recordId?.let { id ->
            val matchedRecord = repository.getRecordById(id)
            if (matchedRecord != null) {
                return RecordResolution.unique(matchedRecord, ListFocusRequest.MATCH_MODE_RECORD_ID)
            }
        }

        val normalizedPhone = normalizePhone(queryPhone)
        if (!normalizedPhone.isNullOrBlank()) {
            val phoneMatches = records.filter { normalizePhone(it.phone) == normalizedPhone }
            if (phoneMatches.size == 1) {
                return RecordResolution.unique(phoneMatches.first(), ListFocusRequest.MATCH_MODE_PHONE)
            }
            if (phoneMatches.isNotEmpty()) {
                return RecordResolution.ambiguous(phoneMatches, ListFocusRequest.MATCH_MODE_AMBIGUOUS)
            }
        }

        position?.takeIf { it in 1..records.size }?.let { requestedPosition ->
            return RecordResolution.unique(
                record = records[requestedPosition - 1],
                matchMode = ListFocusRequest.MATCH_MODE_POSITION
            )
        }

        val normalizedName = queryName?.trim().orEmpty()
        val normalizedCity = queryCity?.trim().orEmpty()
        if (normalizedName.isNotBlank()) {
            val exactMatches = records.filter { record ->
                record.name == normalizedName && (normalizedCity.isBlank() || record.city == normalizedCity)
            }
            if (exactMatches.size == 1) {
                return RecordResolution.unique(exactMatches.first(), ListFocusRequest.MATCH_MODE_NAME)
            }
            if (exactMatches.size > 1) {
                return RecordResolution.ambiguous(exactMatches, ListFocusRequest.MATCH_MODE_AMBIGUOUS)
            }

            val fuzzyMatches = records.filter { record ->
                record.name.contains(normalizedName) &&
                    (normalizedCity.isBlank() || record.city == normalizedCity)
            }
            if (fuzzyMatches.size == 1) {
                return RecordResolution.unique(fuzzyMatches.first(), ListFocusRequest.MATCH_MODE_NAME)
            }
            if (fuzzyMatches.isNotEmpty()) {
                return RecordResolution.ambiguous(fuzzyMatches, ListFocusRequest.MATCH_MODE_AMBIGUOUS)
            }
        }

        if (normalizedCity.isNotBlank()) {
            val cityMatches = records.filter { it.city == normalizedCity }
            if (cityMatches.size == 1) {
                return RecordResolution.unique(cityMatches.first(), ListFocusRequest.MATCH_MODE_CITY)
            }
            if (cityMatches.isNotEmpty()) {
                return RecordResolution.ambiguous(cityMatches, ListFocusRequest.MATCH_MODE_AMBIGUOUS)
            }
        }

        return RecordResolution.none()
    }

    private fun normalizePhone(phone: String?): String? {
        val digitsOnly = phone?.filter(Char::isDigit).orEmpty()
        return digitsOnly.takeIf { it.isNotBlank() }
    }
}

data class RecordResolution(
    val matchedRecord: PersonalInfoRecord? = null,
    val candidates: List<PersonalInfoRecord> = emptyList(),
    val matchMode: String = ListFocusRequest.MATCH_MODE_NONE
) {
    val isUnique: Boolean
        get() = matchedRecord != null

    fun toRecordRef(): RecordRef? {
        val record = matchedRecord ?: candidates.firstOrNull() ?: return null
        return RecordRef(
            recordId = record.recordId,
            displayName = record.name,
            phone = record.phone,
            city = record.city
        )
    }

    fun toListFocusRequest(autoOpenDetail: Boolean = false): ListFocusRequest {
        val targetRecord = matchedRecord ?: candidates.firstOrNull()
        return ListFocusRequest(
            recordId = matchedRecord?.recordId,
            position = targetRecord?.recordId?.substringAfter("record-")?.toIntOrNull(),
            queryName = targetRecord?.name,
            queryPhone = targetRecord?.phone,
            queryCity = targetRecord?.city,
            matchMode = matchMode,
            autoOpenDetail = autoOpenDetail && isUnique,
            candidateCount = if (isUnique) 1 else candidates.size
        )
    }

    companion object {
        fun unique(record: PersonalInfoRecord, matchMode: String): RecordResolution {
            return RecordResolution(
                matchedRecord = record,
                candidates = listOf(record),
                matchMode = matchMode
            )
        }

        fun ambiguous(candidates: List<PersonalInfoRecord>, matchMode: String): RecordResolution {
            return RecordResolution(
                matchedRecord = null,
                candidates = candidates,
                matchMode = matchMode
            )
        }

        fun none(): RecordResolution = RecordResolution()
    }
}
