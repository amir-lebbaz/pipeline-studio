package com.architecture.pipelinestudio.engine

enum class PipelineStage(
    val shortName: String,
    val fullNameEn: String,
    val fullNameAr: String,
    val colorHex: String,
    val textColorHex: String
) {
    // 4-Stage Model (Lecture Chapter 2)
    FETCH_4("F", "Fetch", "جلب التعليمة", "#242D36", "#EDF0E9"),
    DECODE_4("D", "Decode", "فك التشفير وقراءة السجلات", "#536372", "#EDF0E9"),
    EXECUTE_4("E", "Execute", "تنفيذ بالـ UAL", "#384752", "#EDF0E9"),
    WRITEBACK_4("WB", "Write Back", "كتابة النتيجة بالسجل", "#899C96", "#242D36"),

    // 5-Stage Model (TD 4 Exercice 6)
    FETCH_5("IF", "Instruction Fetch", "جلب التعليمة", "#242D36", "#EDF0E9"),
    DECODE_5("ID", "Instruction Decode", "فك التشفير وقراءة السجلات", "#536372", "#EDF0E9"),
    EXECUTE_5("EX", "Execute / Address", "تنفيذ بالـ UAL", "#384752", "#EDF0E9"),
    MEMORY_5("MEM", "Memory Access", "الوصول للذاكرة", "#6B7D82", "#EDF0E9"),
    WRITEBACK_5("WB", "Write Back", "كتابة النتيجة بالسجل", "#899C96", "#242D36"),

    // Stall / Bubble
    STALL("Bulle", "Stall / Bubble", "فقاعة تعطيل (Bulle)", "#E2E7DF", "#536372")
}

enum class PipelineModel(
    val depth: Int,
    val stages: List<PipelineStage>,
    val modelNameEn: String,
    val modelNameAr: String
) {
    FOUR_STAGE(
        depth = 4,
        stages = listOf(
            PipelineStage.FETCH_4,
            PipelineStage.DECODE_4,
            PipelineStage.EXECUTE_4,
            PipelineStage.WRITEBACK_4
        ),
        modelNameEn = "4-Stage (F-D-E-WB)",
        modelNameAr = "4 مراحل (F-D-E-WB)"
    ),
    FIVE_STAGE(
        depth = 5,
        stages = listOf(
            PipelineStage.FETCH_5,
            PipelineStage.DECODE_5,
            PipelineStage.EXECUTE_5,
            PipelineStage.MEMORY_5,
            PipelineStage.WRITEBACK_5
        ),
        modelNameEn = "5-Stage (IF-ID-EX-MEM-WB)",
        modelNameAr = "5 مراحل (IF-ID-EX-MEM-WB)"
    )
}

enum class HazardMode {
    STALLS_ONLY,
    FORWARDING,
    REORDERING
}
