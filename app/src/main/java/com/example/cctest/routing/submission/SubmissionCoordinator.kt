package com.example.cctest.routing.submission

import com.example.cctest.routing.parser.PersonalInfoFields
import kotlinx.coroutines.delay

class SubmissionCoordinator(
    private val submissionPort: SubmissionPort
) {
    suspend fun submit(fields: PersonalInfoFields): SubmissionResult {
        if (fields.name.isNullOrBlank() || fields.phone.isNullOrBlank()) {
            return SubmissionResult(
                isSuccess = false,
                message = "提交前请至少确认姓名和手机号。"
            )
        }
        delay(250)
        return submissionPort.submit(fields)
    }
}
