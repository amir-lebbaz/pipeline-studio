package com.architecture.pipelinestudio.engine

enum class HazardType {
    RAW_DATA,
    WAR_DATA,
    WAW_DATA,
    CONTROL_BRANCH,
    STRUCTURAL
}

data class Hazard(
    val type: HazardType,
    val sourceInstruction: Instruction,
    val targetInstruction: Instruction,
    val registerFamily: String,
    val stallCycles: Int,
    val forwardingSaves: Int = 0,
    val descriptionEn: String,
    val descriptionAr: String,
    val solutionEn: String = "",
    val solutionAr: String = ""
)
