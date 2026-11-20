package com.architecture.pipelinestudio.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.architecture.pipelinestudio.R
import com.architecture.pipelinestudio.databinding.ActivityMainBinding
import com.architecture.pipelinestudio.engine.*
import com.architecture.pipelinestudio.util.LocaleHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Current Simulation State
    private var selectedModel: PipelineModel = PipelineModel.FIVE_STAGE
    private var selectedHazardMode: HazardMode = HazardMode.STALLS_ONLY

    private var monoTypeface: Typeface? = null
    private var sansTypeface: Typeface? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadTypefaces()
        setupLanguageToggle()
        setupModelSwitch()
        setupHazardModeSwitch()
        setupPresetButtons()
        setupActionButtons()

        // Load default course example (TD4 Ex6) and run initial simulation
        loadPresetTD4()
    }

    private fun loadTypefaces() {
        try {
            monoTypeface = ResourcesCompat.getFont(this, R.font.ibm_plex_mono_semibold)
            val currentLang = LocaleHelper.getPersistedLanguage(this)
            sansTypeface = if (currentLang == LocaleHelper.LANG_AR) {
                ResourcesCompat.getFont(this, R.font.ibm_plex_sans_ar_semibold)
            } else {
                ResourcesCompat.getFont(this, R.font.instrument_sans_semibold)
            }
        } catch (_: Exception) {
            monoTypeface = Typeface.MONOSPACE
            sansTypeface = Typeface.SANS_SERIF
        }
    }

    private fun setupLanguageToggle() {
        val currentLang = LocaleHelper.getPersistedLanguage(this)
        binding.btnLangToggle.text = if (currentLang == LocaleHelper.LANG_AR) "EN" else "عربي"

        binding.btnLangToggle.setOnClickListener {
            val targetLang = if (currentLang == LocaleHelper.LANG_AR) LocaleHelper.LANG_EN else LocaleHelper.LANG_AR
            LocaleHelper.applyLocale(this, targetLang)
            recreate()
        }
    }

    private fun setupModelSwitch() {
        binding.btnModel4Stage.setOnClickListener {
            selectedModel = PipelineModel.FOUR_STAGE
            updateModelSwitchUI()
            runSimulation()
        }
        binding.btnModel5Stage.setOnClickListener {
            selectedModel = PipelineModel.FIVE_STAGE
            updateModelSwitchUI()
            runSimulation()
        }
        updateModelSwitchUI()
    }

    private fun updateModelSwitchUI() {
        val activeBg = ContextCompat.getDrawable(this, R.drawable.bg_pill_active)
        val inactiveBg = ContextCompat.getDrawable(this, R.drawable.bg_pill_inactive)
        val activeTextColor = ContextCompat.getColor(this, R.color.spectral_white)
        val inactiveTextColor = ContextCompat.getColor(this, R.color.midnight_ink)

        if (selectedModel == PipelineModel.FOUR_STAGE) {
            binding.btnModel4Stage.background = activeBg
            binding.btnModel4Stage.setTextColor(activeTextColor)
            binding.btnModel5Stage.background = inactiveBg
            binding.btnModel5Stage.setTextColor(inactiveTextColor)
        } else {
            binding.btnModel4Stage.background = inactiveBg
            binding.btnModel4Stage.setTextColor(inactiveTextColor)
            binding.btnModel5Stage.background = activeBg
            binding.btnModel5Stage.setTextColor(activeTextColor)
        }
    }

    private fun setupHazardModeSwitch() {
        binding.btnModeStalls.setOnClickListener {
            selectedHazardMode = HazardMode.STALLS_ONLY
            updateHazardModeUI()
            runSimulation()
        }
        binding.btnModeForwarding.setOnClickListener {
            selectedHazardMode = HazardMode.FORWARDING
            updateHazardModeUI()
            runSimulation()
        }
        binding.btnModeReordering.setOnClickListener {
            selectedHazardMode = HazardMode.REORDERING
            updateHazardModeUI()
            runSimulation()
        }
        updateHazardModeUI()
    }

    private fun updateHazardModeUI() {
        val activeBg = ContextCompat.getDrawable(this, R.drawable.bg_pill_active)
        val inactiveBg = ContextCompat.getDrawable(this, R.drawable.bg_pill_inactive)
        val activeTextColor = ContextCompat.getColor(this, R.color.spectral_white)
        val inactiveTextColor = ContextCompat.getColor(this, R.color.midnight_ink)

        binding.btnModeStalls.background = if (selectedHazardMode == HazardMode.STALLS_ONLY) activeBg else inactiveBg
        binding.btnModeStalls.setTextColor(if (selectedHazardMode == HazardMode.STALLS_ONLY) activeTextColor else inactiveTextColor)

        binding.btnModeForwarding.background = if (selectedHazardMode == HazardMode.FORWARDING) activeBg else inactiveBg
        binding.btnModeForwarding.setTextColor(if (selectedHazardMode == HazardMode.FORWARDING) activeTextColor else inactiveTextColor)

        binding.btnModeReordering.background = if (selectedHazardMode == HazardMode.REORDERING) activeBg else inactiveBg
        binding.btnModeReordering.setTextColor(if (selectedHazardMode == HazardMode.REORDERING) activeTextColor else inactiveTextColor)
    }

    private fun setupPresetButtons() {
        binding.presetTd4.setOnClickListener { loadPresetTD4() }
        binding.presetSlide29.setOnClickListener { loadPresetSlide29() }
        binding.presetBranch.setOnClickListener { loadPresetBranch() }
        binding.preset4Stage.setOnClickListener { loadPreset4Stage() }
    }

    private fun loadPresetTD4() {
        // From TD 4 Exercice 6
        binding.etAssemblyCode.setText(
            """
            MOV RAX, [RBX]
            ADD RAX, RCX
            JMP L
            """.trimIndent()
        )
        selectedModel = PipelineModel.FIVE_STAGE
        selectedHazardMode = HazardMode.STALLS_ONLY
        updateModelSwitchUI()
        updateHazardModeUI()
        runSimulation()
    }

    private fun loadPresetSlide29() {
        // From Chapter 2, Slide 29
        binding.etAssemblyCode.setText(
            """
            A = B + C
            D = A + C
            E = F + B
            """.trimIndent()
        )
        selectedModel = PipelineModel.FOUR_STAGE
        selectedHazardMode = HazardMode.REORDERING
        updateModelSwitchUI()
        updateHazardModeUI()
        runSimulation()
    }

    private fun loadPresetBranch() {
        binding.etAssemblyCode.setText(
            """
            MOV AX, 1
            JMP Next
            ADD BX, 1
            ADD AX, BX
            """.trimIndent()
        )
        selectedModel = PipelineModel.FOUR_STAGE
        selectedHazardMode = HazardMode.STALLS_ONLY
        updateModelSwitchUI()
        updateHazardModeUI()
        runSimulation()
    }

    private fun loadPreset4Stage() {
        // From Chapter 2, Slide 12
        binding.etAssemblyCode.setText(
            """
            MOV AX, 9
            ADD AX, 2
            JMP Loop
            """.trimIndent()
        )
        selectedModel = PipelineModel.FOUR_STAGE
        selectedHazardMode = HazardMode.FORWARDING
        updateModelSwitchUI()
        updateHazardModeUI()
        runSimulation()
    }

    private fun setupActionButtons() {
        binding.btnSimulate.setOnClickListener { runSimulation() }
        binding.btnClear.setOnClickListener {
            binding.etAssemblyCode.setText("")
            runSimulation()
        }
    }

    private fun runSimulation() {
        val rawCode = binding.etAssemblyCode.text?.toString() ?: ""
        val instructions = Parser.parse(rawCode)
        val result = PipelineSimulator.simulate(instructions, selectedModel, selectedHazardMode)

        renderHeroMetrics(result)
        renderChronogram(result)
        renderHazards(result)
    }

    private fun renderHeroMetrics(result: SimulationResult) {
        binding.tvTotalCycles.text = String.format("%02d", result.totalCycles)
        binding.tvSpeedup.text = String.format("%.2fx", result.speedup)
        binding.tvStatStages.text = result.model.depth.toString()
        binding.tvStatStalls.text = String.format("%02d", result.totalStalls)
        binding.tvStatHazards.text = String.format("%02d", result.hazards.size)
    }

    /**
     * Renders the execution chronogram in a horizontal scrollable grid.
     * Locked strictly to LTR layout direction so time and code never invert.
     */
    private fun renderChronogram(result: SimulationResult) {
        val grid = binding.llChronogramGrid
        grid.removeAllViews()

        if (result.schedules.isEmpty() || result.totalCycles == 0) {
            val emptyTv = TextView(this).apply {
                text = getString(R.string.chronogram_hint)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.stormy_slate))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = sansTypeface
                setPadding(8, 16, 8, 16)
            }
            grid.addView(emptyTv)
            return
        }

        val cellWidthPx = dpToPx(52)
        val instColWidthPx = dpToPx(120)
        val cellHeightPx = dpToPx(34)
        val headerHeightPx = dpToPx(28)
        val gapPx = dpToPx(4)

        // 1. Header Row (Inst | CC1 | CC2 | ... | CCn)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, gapPx)
        }

        // Header: Inst column
        val instColHeader = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(instColWidthPx, headerHeightPx).apply {
                marginEnd = gapPx
            }
            text = getString(R.string.inst_column_header)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.midnight_ink))
            typeface = monoTypeface
        }
        headerRow.addView(instColHeader)

        // Header: Cycle columns CC1..CCn
        for (c in 1..result.totalCycles) {
            val ccHeader = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(cellWidthPx, headerHeightPx).apply {
                    marginEnd = gapPx
                }
                text = "${getString(R.string.cycle_prefix)}$c"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.stormy_slate))
                typeface = monoTypeface
            }
            headerRow.addView(ccHeader)
        }
        grid.addView(headerRow)

        // 2. Data Rows: Each instruction schedule
        for (schedule in result.schedules) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, gapPx)
            }

            // Instruction title column: e.g. "I1: MOV RAX,[RBX]"
            val instTitle = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(instColWidthPx, cellHeightPx).apply {
                    marginEnd = gapPx
                }
                text = "I${schedule.instruction.id}: ${schedule.instruction.rawText}"
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.midnight_ink))
                typeface = monoTypeface
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            row.addView(instTitle)

            // Cycle cells
            for (c in 1..result.totalCycles) {
                val cellExecution = schedule.stages[c]
                val cellView = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(cellWidthPx, cellHeightPx).apply {
                        marginEnd = gapPx
                    }
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    typeface = monoTypeface

                    if (cellExecution != null) {
                        if (cellExecution.isStall) {
                            text = getString(R.string.stall_bulle)
                            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_stage_stall)
                            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.stage_stall_text))
                        } else {
                            text = cellExecution.stage.shortName
                            background = createStageDrawable(cellExecution.stage.colorHex)
                            setTextColor(Color.parseColor(cellExecution.stage.textColorHex))
                        }
                    } else {
                        text = ""
                        background = null
                    }
                }
                row.addView(cellView)
            }
            grid.addView(row)
        }
    }

    private fun createStageDrawable(colorHex: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(6).toFloat()
            setColor(Color.parseColor(colorHex))
        }
    }

    private fun renderHazards(result: SimulationResult) {
        val list = binding.llHazardList
        list.removeAllViews()

        val isArabic = LocaleHelper.getPersistedLanguage(this) == LocaleHelper.LANG_AR

        // 1. If Reordering mode is active and swaps were performed, display Reorder Banner Card
        if (result.hazardMode == HazardMode.REORDERING && result.reorderedInstructions != null && result.cyclesSavedByReordering > 0) {
            val reorderCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_stat_tile)
                setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(10)
                }
                layoutParams = lp
            }

            // Banner Title & Savings Pill
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleTv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = getString(R.string.reordering_applied_title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.midnight_ink))
                typeface = sansTypeface
            }

            val badgeTv = TextView(this).apply {
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_pill_accent)
                text = "-${result.cyclesSavedByReordering} ${getString(R.string.stat_stalls_label)}"
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.midnight_ink))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = monoTypeface
                setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3))
            }

            topRow.addView(titleTv)
            topRow.addView(badgeTv)
            reorderCard.addView(topRow)

            // Reordered sequence code block (Technical LTR Lock)
            val codeTv = TextView(this).apply {
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                textDirection = View.TEXT_DIRECTION_LTR
                val seq = result.reorderedInstructions.joinToString("\n") { "I${it.id}: ${it.rawText}" }
                text = seq
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.midnight_ink))
                typeface = monoTypeface
                setPadding(0, dpToPx(8), 0, dpToPx(4))
            }
            reorderCard.addView(codeTv)

            val explanationTv = TextView(this).apply {
                text = if (isArabic) result.reorderingExplanationAr else result.reorderingExplanationEn
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.stormy_slate))
                typeface = sansTypeface
            }
            reorderCard.addView(explanationTv)

            list.addView(reorderCard)
        }

        // 2. Detected Hazards
        if (result.hazards.isEmpty()) {
            val okCard = TextView(this).apply {
                text = getString(R.string.no_hazards_detected)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.stormy_slate))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = sansTypeface
                setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
            }
            list.addView(okCard)
            return
        }

        for (hazard in result.hazards) {
            val itemCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_stat_tile)
                setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(8)
                }
                layoutParams = lp
            }

            // Top Row: Hazard Title + Stalls Badge
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleTv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = when (hazard.type) {
                    HazardType.RAW_DATA -> getString(R.string.hazard_raw_title)
                    HazardType.CONTROL_BRANCH -> getString(R.string.hazard_control_title)
                    HazardType.STRUCTURAL -> getString(R.string.hazard_structural_title)
                }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.midnight_ink))
                typeface = sansTypeface
            }

            val badgeTv = TextView(this).apply {
                background = if (hazard.forwardingSaves > 0) {
                    ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_pill_accent)
                } else {
                    ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_pill_active)
                }
                text = "${hazard.stallCycles} Stalls"
                setTextColor(
                    if (hazard.forwardingSaves > 0) ContextCompat.getColor(this@MainActivity, R.color.midnight_ink)
                    else ContextCompat.getColor(this@MainActivity, R.color.spectral_white)
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = monoTypeface
                setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3))
            }

            headerRow.addView(titleTv)
            headerRow.addView(badgeTv)
            itemCard.addView(headerRow)

            // Detail line
            val detailTv = TextView(this).apply {
                text = if (isArabic) hazard.descriptionAr else hazard.descriptionEn
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.stormy_slate))
                typeface = sansTypeface
                setPadding(0, dpToPx(4), 0, 0)
            }
            itemCard.addView(detailTv)

            list.addView(itemCard)
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
