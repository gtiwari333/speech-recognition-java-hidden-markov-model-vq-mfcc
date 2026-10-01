# Speech pipeline visualization: design

Date: 2026-10-01
Status: draft for review

## Goal

Show every step of the speech recognition algorithm in the Swing UI, for each recording that is verified or
recognized and for each word that is trained. The views serve two purposes at once:

- **teaching**: clear, labelled, step-by-step plots with a short explanation of what each step does;
- **debugging**: exact values (tooltips, zoom), the scores of every word model and the best state paths, so
  it is possible to see *why* a recording was misrecognized.

Interaction model: **run, then inspect**. A job runs in the background with the UI responsive; when it
finishes, all of its steps can be browsed.

### Success criteria

1. After Verify / Recognize (recorded audio or a WAV file) the inspector shows all 8 recognition steps for that
   recording, including a ranked list of every word's Viterbi score.
2. After Generate Codebook and Run HMM Train the inspector shows the codebook (LBG) progress and, for any chosen
   word, its training sequences, Baum-Welch convergence and learned matrices.
3. Recognition results and trained models are **unchanged**: same recognized words, byte-identical model files.
4. The window never freezes during a job; failures show a readable message.
5. `mvn package` still produces a runnable `java -jar` GUI.

### Out of scope

- the mouse gesture project;
- live-updating plots while an algorithm runs (only textual progress is shown during a job);
- a history of past runs (the inspector shows the most recent job);
- changes to the algorithms or their parameters.

## Architecture

```
 UI tabs (existing) ──SwingWorker──► Operations.*WithTrace(...) ──► PreProcess / FeatureExtract / Codebook / HiddenMarkov
        │                                    │                              │ (each fills its own trace record)
        │                                    ▼                              ▼
        └──────────────► Inspector ◄── RecognitionTrace / CodebookTrace / List<WordTrainingTrace>
                         (read only: renders records, never calls the algorithm)
```

- New package `org.ioe.tprsa.trace`: immutable Java `record`s holding intermediate results.
- New package `org.ioe.tprsa.ui.viz`: inspectors, one `StepView` per step, a small chart factory.
- Chart library: **JFreeChart** (`org.jfree:jfreechart`, 1.5.x), bundled into the jar with `maven-shade-plugin`.

## 1. Data model (`org.ioe.tprsa.trace`)

### Recognition / verification

| Record | Contents | Filled by |
|---|---|---|
| `PreprocessTrace` | sample rate; normalised signal; end point detection: noise mean and σ, voiced flag per 10 ms frame, trimmed signal; frame size and hop; Hamming window; raw frames and pre-emphasised + windowed frames | `PreProcess`, `EndPointDetection` (expose what they already compute) |
| `FeatureTrace` | per frame: magnitude spectrum, log mel filter bank energies, MFCC before and after cepstral mean normalisation, ΔMFCC, ΔΔMFCC, log energy, Δ and ΔΔ log energy, the final 39-dimensional vectors; the mel filter centre bins (to draw the filter bank) | `MFCC`, `FeatureExtract` |
| `VqTrace` | codeword index per frame; distance to that codeword per frame | `Codebook.quantize` |
| `WordScore` | word; Viterbi log score; best state path q[t]; Viterbi score grid (frames × states) | `HiddenMarkov.viterbi` |
| `RecognitionTrace` | the records above; word scores sorted best first; recognised word; expected word (Verify only, else null); source (recording or file name) | `Operations` |

### Training

| Record | Contents | Filled by |
|---|---|---|
| `CodebookTrace` | for each codebook size reached by splitting (1, 2, 4 … 256): distortion after every k-means iteration; final number of training vectors per codeword; total number of training vectors | `Codebook.initialize` |
| `WordTrainingTrace` | word; training file names; each recording's codeword sequence; log-likelihood per Baum-Welch iteration; converged flag (stopped by the threshold rather than the 50 iteration limit); initial and final transition matrix (6×6) and output matrix (6×256); skipped flag with reason (e.g. no WAV files) | `HiddenMarkov.train`, `Operations` |

### API changes (backwards compatible)

| Class | Change |
|---|---|
| `Operations` | new `RecognitionTrace recognizeWithTrace(float[] samples, String expectedWord)` and `recognizeWithTrace(File wav, String expectedWord)`; new `CodebookTrace generateCodebookWithTrace(Consumer<String> progress)` and `List<WordTrainingTrace> hmmTrainWithTrace(Consumer<String> progress)`. Existing `hmmGetWordFrom…`, `generateCodebook()` and `hmmTrain()` become thin wrappers with unchanged results. New constructor `Operations(Path baseDir)`; `new Operations()` keeps using the working directory. |
| `ObjectIODataBase`, `TrainingTestingWaveFiles` | accept the base directory used by `Operations(Path)` |
| `HiddenMarkov` | `train()` returns the per-iteration log-likelihoods (was `void`); `viterbi()` keeps its score grid, readable after the call |
| `MFCC` | new method returning one frame's intermediate arrays (spectrum, log filter bank, cepstra); `doMFCC` delegates to it |
| `PreProcess`, `EndPointDetection`, `FeatureExtract`, `Codebook` | expose the values listed above through getters or a trace accessor |

No computation changes. Memory: for a 1 s recording (~85 frames) the largest arrays are 85×257 spectra and
85×39 features, well under 1 MB per trace. Training traces keep codeword sequences only, not full per-recording
feature traces.

## 2. UI (`org.ioe.tprsa.ui.viz`)

### Main window

Resizable, about 1200×750, split horizontally:

- **left**: the existing three tabs (Verify Word, Add Sample, Run HMM Train), unchanged, in a fixed-width pane
  (their absolute layouts are kept);
- **right**: the inspector for the most recent job: a step list, the plot area for the selected step, and an
  explanation strip (1–3 sentences on what the step does and its parameters, plus the key numbers of this run).

A **frame slider** is shared by the time-based recognition steps; single-frame plots follow it and time plots
draw a cursor line. Every chart supports tooltips with exact values, zoom by mouse drag and right-click
"Save as PNG" (JFreeChart built-ins).

### Recognition / Verify inspector

| # | Step | Plots | Key numbers |
|---|---|---|---|
| 1 | Waveform & end point detection | normalised waveform; 200 ms noise window and kept (voiced) 10 ms frames shaded | duration, noise μ/σ, 3σ threshold, % of signal kept |
| 2 | Framing & windowing | trimmed signal with frame boundaries; selected frame raw, pre-emphasised and windowed overlaid, with the Hamming curve | 512 samples (23.2 ms), hop 256, pre-emphasis 0.95 |
| 3 | Spectrum & mel filter bank | selected frame's magnitude spectrum (dB) with the 30 triangular filters overlaid; bar chart of that frame's 30 log filter bank energies | 80–11025 Hz, mel formula |
| 4 | MFCC | heat maps over time: log mel energies (frames × 30), MFCC after CMN (frames × 12) | DCT with √(2/N), CMN |
| 5 | Deltas & energy | heat maps of ΔMFCC and ΔΔMFCC; line chart of log energy, Δ, ΔΔ | regression windows 2 / 1, edge frames replicated |
| 6 | Vector quantization | codeword index per frame; distance to the chosen codeword per frame | 256 codewords; mean quantization distance |
| 7 | Word scores | bar chart of every word's Viterbi log score, ranked; recognised word highlighted; expected word marked (Verify) | margin between the best and second-best word |
| 8 | Best path | for a chosen word (default: recognised word, plus the expected word if different): state path over time above the codeword sequence; Viterbi score grid heat map (frames × 6 states) | left-to-right model, jumps of at most 2 states |

### Training inspector

| # | Step | Plots |
|---|---|---|
| 1 | Codebook (LBG) | distortion per k-means iteration, one series per codebook size (1 → 256); bar chart of training vectors per codeword. Shows "run Generate Codebook to see this step" when only HMM training was run. |
| 2 | Training sequences | word picker; each recording's codeword sequence as one row of a heat map |
| 3 | Baum-Welch convergence | word picker; log-likelihood per iteration; marker where it stopped (converged / 50 iteration limit) |
| 4 | Learned model | word picker; transition matrix heat map (6×6, values in the cells); output matrix heat map (6 × 256) |
| 5 | Summary | table: word, recordings, iterations, final log-likelihood, converged, skipped (with reason) |

### Components

| Component | Responsibility |
|---|---|
| `StepView<T>` | interface: `title()`, `explanation(T trace)`, `JComponent build(T trace)`; one small class per step |
| `RecognitionInspector`, `TrainingInspector` | step list + `CardLayout` of the selected step; word picker and frame slider state |
| `Charts` | factory for line, bar and heat-map charts (JFreeChart `XYPlot`, `CategoryPlot`, `XYBlockRenderer`) |
| `HMM_VQ_Speech_Recognition` | window layout, `SwingWorker` jobs, passes finished traces to the inspectors |

Inspectors and step views only read trace records; they never call the algorithm classes.

## 3. Threading and error handling

- Each job (Verify, Recognize recorded, Recognize WAV file, Generate Codebook, Run HMM Train) runs in a
  `SwingWorker`. `Operations` is not thread safe, so starting a job disables all job buttons until it finishes;
  a status line and an indeterminate progress bar show the job is running.
- Training reports progress through the `Consumer<String>` callback (e.g. "Codebook: 64 → 128 codewords",
  "Training word 3/5: Hello"), published to the status line. Plotted data comes only from the finished trace.
- `done()` hands the trace to the inspector and updates the result label and the ✔/✘ verify result as today. On
  failure the status line and a dialog show the message; the inspector keeps the previous trace.

| Situation | Behaviour |
|---|---|
| no codebook or HMM models | message "Train first: no codebook / HMM models found" instead of a NullPointerException |
| recording too short or silent | recognition runs (the pipeline handles it); step 1 notes "no speech detected, whole signal used" |
| WAV that is not 16-bit mono 22050 Hz | rejected with "expected 16-bit mono 22050 Hz, got …" (the models are trained at 22050 Hz) |
| training word folder without WAV files | word skipped; listed with the reason in the training summary |

## 4. Testing

**Trace correctness** (JUnit, no UI)

- For every `TrainWav` file: `recognizeWithTrace` gives the same word as `hmmGetWordFromFile`; word scores contain
  every registered word, sorted best first; the top score equals the recognised word's Viterbi score.
- Features have frames × 39 values and equal the features used for recognition; codeword indices equal
  `Codebook.quantize` of those features and lie in 0..255.
- Viterbi path: one state per frame, never moves backwards, moves at most 2 states per step; the best value in the
  last row of the score grid equals the score.
- Synthetic training data: Baum-Welch log-likelihood never decreases between iterations; k-means distortion
  never increases within a codebook size; traced final matrices equal the saved model.

**Behaviour unchanged**

- `generateCodebookWithTrace` + `hmmTrainWithTrace` on a temporary copy of `TrainWav` (via `Operations(Path)`)
  produce model files byte-identical to the ones in `models/` (training is seeded and deterministic).
- The existing end-to-end recognition test keeps passing.

**UI** (headless, `java.awt.headless=true`)

- Every step view builds its component from a fixture trace without exceptions; every chart renders to an image.
- Each inspector can select every step and every word in its word picker.

**Build**: the shaded jar contains JFreeChart and the main class manifest entry.

**Manual check**: launch the GUI; record and verify a word, recognise a WAV file, generate the codebook and train;
confirm every step shows sensible plots and the window stays responsive.
