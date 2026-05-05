package com.example.cctest.routing.submission

import com.example.cctest.routing.parser.PersonalInfoFields

data class SubmissionResult(
    val isSuccess: Boolean,
    val message: String
)

interface SubmissionPort {
    suspend fun submit(fields: PersonalInfoFields): SubmissionResult
}

class FakeSubmissionPort : SubmissionPort {
    override suspend fun submit(fields: PersonalInfoFields): SubmissionResult {
        return SubmissionResult(
            isSuccess = true,
            message = "个人信息工作流已通过模拟提交完成。"
        )
    }
}
