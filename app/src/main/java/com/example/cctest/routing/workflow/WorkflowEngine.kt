package com.example.cctest.routing.workflow

class WorkflowEngine {
    fun start(definition: WorkflowDefinition): WorkflowStep {
        return definition.steps.first()
    }

    fun next(definition: WorkflowDefinition, currentStepId: String?): WorkflowStep? {
        val currentIndex = definition.steps.indexOfFirst { it.stepId == currentStepId }
        if (currentIndex == -1) {
            return definition.steps.firstOrNull()
        }
        return definition.steps.getOrNull(currentIndex + 1)
    }

    fun findStep(definition: WorkflowDefinition, stepId: String): WorkflowStep? {
        return definition.steps.firstOrNull { it.stepId == stepId }
    }
}
