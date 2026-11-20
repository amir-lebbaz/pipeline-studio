# Pipeline Studio

Offline native Android CPU Pipeline and Hazards Simulator designed for Computer Architecture II (`Architecture des ordinateurs II`, UEF31) at Université Amar Telidji – Laghouat.

**Author**: Amir Lebbaz ([@amir-lebbaz](https://github.com/amir-lebbaz))  
Computer Science Student, Université Amar Telidji – Laghouat

---

## Direct Download (APK)

Get the latest build directly on your Android phone without needing Android Studio or an IDE:

- **Download APK**: [Pipeline Studio v1.0 on GitHub Releases](https://github.com/amir-lebbaz/pipeline-studio/releases/latest)
- **Direct APK Link**: [PipelineStudio-v1.0.apk](https://github.com/amir-lebbaz/pipeline-studio/releases/download/v1.0/PipelineStudio-v1.0.apk)

The application runs 100% offline, requires zero special permissions, and has an APK size under 5 MB.

---

## Overview

Pipeline Studio simulates instruction-level pipelining and hazard resolution for x86 and 8086 assembly code. It provides an interactive cycle-by-cycle chronogram grid and evaluates processor performance metrics directly aligned with course examinations and laboratory exercises.

### Pipeline Models
- **4-Stage Model (`F - D - E - WB`)**:
  - `F` (Fetch): Instruction fetch from memory.
  - `D` (Decode): Instruction decode and register operand read.
  - `E` (Execute): ALU operation and effective address calculation.
  - `WB` (Write Back): Register file write.
  - Aligned with Chapter 2 lecture slides.

- **5-Stage Model (`IF - ID - EX - MEM - WB`)**:
  - `IF` (Instruction Fetch): Instruction cache access.
  - `ID` (Instruction Decode): Register file read and hazard check.
  - `EX` (Execution): ALU execution.
  - `MEM` (Memory Access): Data memory read/write.
  - `WB` (Write Back): Register commit.
  - Aligned with TD 4 Exercise 6.

### Hazard Detection and Mitigation
- **Data Hazards (RAW - Read After Write)**:
  - Tracks true dependencies across instructions.
  - Features an x86 sub-register aliasing engine that groups related sub-registers (e.g., `RAX`, `EAX`, `AX`, `AH`, `AL`) into unified physical register families.
- **Control Hazards**:
  - Simulates branch penalties and pipeline flushes (3 bubble cycles for 4-stage pipelines).
- **Hazard Resolution Modes**:
  - **Stalls Only (`Blocage`)**: Injects bubble cycles until dependent operands are committed at `WB`.
  - **Data Forwarding (`Anticipation`)**: Bypasses results directly from `E`/`EX` or `MEM` stages. Load-Use dependencies correctly maintain a single stall cycle as established in TD 4 Exercise 6.
  - **Instruction Reordering (`Reordonnancement`)**: Automatically reschedules independent instructions into pipeline delay slots without altering program semantics (preserving RAW, WAR, WAW, and control boundaries), per Chapter 2 Slide 29.

### Performance Metrics
- Total execution cycles ($T_k$) vs unpipelined cycles ($T_1$).
- Speedup factor:
  ```text
  S = T1 / Tk = (N * k) / (k + N - 1 + Stalls)
  ```
- Cumulative stall count and hazard diagnostic breakdown.

---

## User Interface & Design

- **Bento Card Hierarchy**: Clean contrast between primary dark performance cards and elevated light surfaces with 1dp hairline borders.
- **Offline Typography**: Bundles `Instrument Sans` (English), `IBM Plex Sans Arabic` (Arabic), and `IBM Plex Mono` (assembly code, line numbers, and tabular cycle counts) locally in `res/font/`.
- **Bilingual Support (Arabic & English)**: Instant language toggle with persistent storage via `SharedPreferences`.
- **Technical LTR Lock**: The assembly code editor, mnemonics, pipeline stage badges, and horizontal execution chronogram (`CC1 -> CCn`) remain strictly LTR in both Arabic and English modes to preserve technical readability.

---

## Project Structure

```text
pipeline_studio/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/architecture/pipelinestudio/
│   │   │   │   ├── engine/          # Pure Kotlin simulation engine
│   │   │   │   │   ├── Instruction.kt
│   │   │   │   │   ├── Parser.kt
│   │   │   │   │   ├── PipelineStage.kt
│   │   │   │   │   ├── RegisterAlias.kt
│   │   │   │   │   ├── Hazard.kt
│   │   │   │   │   ├── ReorderingEngine.kt
│   │   │   │   │   └── PipelineSimulator.kt
│   │   │   │   ├── ui/              # Bento UI activity and chronogram grid
│   │   │   │   │   └── MainActivity.kt
│   │   │   │   ├── util/            # Locale management (AR / EN)
│   │   │   │   │   └── LocaleHelper.kt
│   │   │   │   └── PipelineStudioApp.kt
│   │   │   └── res/                 # Layouts, vector drawables, offline fonts
│   │   └── test/                    # JUnit4 engine verification tests
│   └── build.gradle.kts
├── .github/workflows/
│   └── build_apk.yml                # CI workflow for building and releasing APK
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## Building Locally

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK (API 34, minimum API 24)

### Steps
1. Clone the repository:
   ```bash
   git clone https://github.com/amir-lebbaz/pipeline-studio.git
   cd pipeline-studio
   ```
2. Build the project using Gradle:
   ```bash
   ./gradlew assembleDebug
   ```
3. Run unit tests:
   ```bash
   ./gradlew testDebugUnitTest
   ```
4. Generated APK location:
   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

---

## License

This project is developed for educational purposes under the academic curriculum of Université Amar Telidji – Laghouat.
