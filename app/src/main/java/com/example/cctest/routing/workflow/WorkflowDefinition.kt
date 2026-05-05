package com.example.cctest.routing.workflow

data class WorkflowDefinition(
    val workflowId: String,
    val steps: List<WorkflowStep>
)

data class WorkflowStep(
    val stepId: String,
    val destinationId: Int
)
