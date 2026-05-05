package com.example.cctest.routing.workflow

import com.example.cctest.R

class WorkflowRegistry {
    fun personalInfoWorkflow(): WorkflowDefinition {
        return WorkflowDefinition(
            workflowId = PERSONAL_INFO_WORKFLOW_ID,
            steps = listOf(
                WorkflowStep(STEP_FORM, R.id.personalInfoFormStepFragment),
                WorkflowStep(STEP_REVIEW, R.id.reviewSubmitFragment),
                WorkflowStep(STEP_RESULT, R.id.resultFragment)
            )
        )
    }

    companion object {
        const val PERSONAL_INFO_WORKFLOW_ID = "personal_info_workflow"
        const val STEP_FORM = "form"
        const val STEP_REVIEW = "review"
        const val STEP_RESULT = "result"
    }
}
