# Speech Pipeline Visualization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show every step of the speech recognition algorithm (pre-processing → MFCC → VQ → HMM) in the Swing UI for each verified/recognized recording and each trained word.

**Architecture:** Each pipeline stage records what it computed into immutable `record`s in `org.ioe.tprsa.trace`; `Operations` gains `*WithTrace` methods that return them (the old methods become thin wrappers). A new `org.ioe.tprsa.ui.viz` package renders the records with JFreeChart, one small `StepView` class per step, inside a recognition and a training inspector. Jobs run in a `SwingWorker` so the window stays responsive.

**Tech Stack:** Java 17, Swing, JFreeChart 1.5.6, JUnit 5.11, Maven (shade plugin for the runnable jar).

**Spec:** `docs/superpowers/specs/2026-10-01-speech-pipeline-visualization-design.md`

## Global Constraints

- Java 17 (`maven.compiler.release` 17); tests run headless (`java.awt.headless=true`) with the project root as working directory (already configured in `pom.xml`).
- Only new runtime dependency: `org.jfree:jfreechart:1.5.6`; the runnable jar bundles it with `maven-shade-plugin` 3.6.0.
- **Do not modify the serialized classes** `Points`, `Centroid`, `CodeBookDictionary`, `HMMModel`, `FeatureVector`: `Centroid`/`Points` have no explicit `serialVersionUID`, so any change breaks reading `models/` and byte-identical retraining.
- **No computation changes**: recognized words and trained model files must stay identical (Tasks 1, 5, 6 pin this).
- Code style: match the file being edited. New files use tabs and `( x )` spacing like `Operations.java`.
- Commits: the user makes the commits. Each task ends with a **Checkpoint** (full test suite) instead of a commit; if the user asks for commits, commit at each checkpoint.

## Review Focus

1. **Recognize/verify before any training** (no codebook or models): a readable "Train first: …" message, no stack trace or NullPointerException. → Task 5 test `recognizingWithoutModelsAsksToTrainFirst`, Task 12 failure path.
2. **A WAV that is not 16-bit mono 22050 Hz** (e.g. 8 kHz): rejected with "expected 16-bit mono 22050 Hz, got …" instead of silently wrong features. → Task 5 test `wavWithAnotherSampleRateIsRejected`.
3. **A very short or silent recording**: a trace is still produced, every view renders, step 1 says "No speech detected". → Task 2 tests, Task 5 test `silentRecordingStillGivesATrace`, Task 9 test `viewsRenderASilentRecording`.
4. **Generate Codebook clicked twice in one session**: today the second run accumulates the first run's features (`allFeaturesList` is a field that is never cleared); both runs must give the same codebook. → Task 1 test `generatingTheCodebookTwiceGivesTheSameCodebook`.
5. **Clicking another job button while a job runs**: the second click is ignored and job buttons are disabled until the job ends. → Task 12 test `secondJobIsIgnoredWhileOneRuns`.

---

## File Structure

```
src/org/ioe/tprsa/
  trace/                                  NEW: immutable records, no dependencies
    PreprocessTrace.java  FeatureTrace.java  MfccFrame.java  VqTrace.java  WordScore.java
    RecognitionTrace.java  CodebookTrace.java  WordTrainingTrace.java  TrainingSession.java
  db/ObjectIODataBase.java                MODIFY: base directory
  db/TrainingTestingWaveFiles.java        MODIFY: base directory
  audio/preProcessings/EndPointDetection.java  MODIFY: expose noise stats, voiced frames
  audio/PreProcess.java                   MODIFY: keep pre-emphasised frames, toTrace()
  audio/feature/MFCC.java                 MODIFY: computeFrame(), getMelCentreBins()
  audio/FeatureExtract.java               MODIFY: keep spectra/log mel/raw cepstra, toTrace()
  classify/speech/vq/Codebook.java        MODIFY: load/save with DataBase, quantizeWithTrace(), split traces
  classify/speech/HiddenMarkov.java       MODIFY: load/save with DataBase, Viterbi grid, train() returns LL
  mediator/Operations.java                MODIFY: base dir, *WithTrace methods, wrappers
  ui/JobRunner.java                       NEW: SwingWorker jobs, disables job buttons
  ui/HMM_VQ_Speech_Recognition.java       MODIFY: split window, inspectors, status bar, jobs
  ui/viz/                                 NEW
    ViewState.java  StepView.java  Charts.java  Inspector.java
    RecognitionInspector.java  TrainingInspector.java
    recognition/ WaveformStep  FramingStep  SpectrumStep  MfccStep  DeltaEnergyStep  VqStep  ScoresStep  BestPathStep
    training/    CodebookStep  TrainingSequencesStep  ConvergenceStep  ModelStep  SummaryStep
test/org/ioe/tprsa/
  TestFiles.java                          NEW: copyTree helper
  mediator/OperationsBaseDirTest.java  OperationsRecognitionTraceTest.java  OperationsTrainingTraceTest.java
  audio/PreprocessTraceTest.java  audio/FeatureTraceTest.java
  classify/speech/vq/CodebookTest.java  classify/speech/HiddenMarkovTest.java   (extend)
  ui/JobRunnerTest.java
  ui/viz/ViewTestSupport.java  TraceFixtures.java  ChartsTest.java  RecognitionViewsTest.java
          TrainingViewsTest.java  InspectorTest.java
```

---

### Task 1: Configurable base directory (and the codebook-twice fix)

**Files:**
- Modify: `src/org/ioe/tprsa/db/ObjectIODataBase.java`, `src/org/ioe/tprsa/db/TrainingTestingWaveFiles.java`
- Modify: `src/org/ioe/tprsa/classify/speech/HiddenMarkov.java`, `src/org/ioe/tprsa/classify/speech/vq/Codebook.java`
- Modify: `src/org/ioe/tprsa/mediator/Operations.java`
- Create: `test/org/ioe/tprsa/TestFiles.java`, `test/org/ioe/tprsa/mediator/OperationsBaseDirTest.java`

**Interfaces:**
- Produces: `new Operations( Path baseDir )`; `new ObjectIODataBase( Path baseDir )`; `new TrainingTestingWaveFiles( String testOrTrain, Path baseDir )`; `new HiddenMarkov( String word, DataBase db )`; `HiddenMarkov.save( String name, DataBase db )`; `new Codebook( DataBase db )`; `Codebook.saveToFile( DataBase db )`; test helper `TestFiles.copyTree( Path from, Path to )`. All no-arg/old variants delegate with the working directory.

- [ ] **Step 1: Write the failing test**

`test/org/ioe/tprsa/TestFiles.java`:
```java
package org.ioe.tprsa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class TestFiles {

	private TestFiles( ) {
	}

	/** recursive copy of a folder */
	public static void copyTree( Path from, Path to ) throws IOException {
		try ( Stream< Path > paths = Files.walk( from ) ) {
			for ( Path p : ( Iterable< Path > ) paths::iterator ) {
				Path target = to.resolve( from.relativize( p ).toString( ) );
				if ( Files.isDirectory( p ) ) {
					Files.createDirectories( target );
				} else {
					Files.copy( p, target );
				}
			}
		}
	}
}
```

`test/org/ioe/tprsa/mediator/OperationsBaseDirTest.java`:
```java
package org.ioe.tprsa.mediator;

import org.ioe.tprsa.TestFiles;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag( "integration" )
class OperationsBaseDirTest {

	/** every file in the project's models/ folder must be byte-identical to the one trained into dir */
	static void assertModelsIdentical( Path dir ) throws Exception {
		Path models = Paths.get( "models" );
		try ( Stream< Path > files = Files.walk( models ) ) {
			for ( Path f : ( Iterable< Path > ) files.filter( Files::isRegularFile )::iterator ) {
				Path trained = dir.resolve( "models" ).resolve( models.relativize( f ).toString( ) );
				assertTrue( Files.exists( trained ), "not trained: " + trained );
				assertArrayEquals( Files.readAllBytes( f ), Files.readAllBytes( trained ),
						f + " differs (retrain models/ if TrainWav/ changed)" );
			}
		}
	}

	@Test
	void trainsIntoTheBaseDirAndReproducesTheProjectModels( @TempDir Path dir ) throws Exception {
		TestFiles.copyTree( Paths.get( "TrainWav" ), dir.resolve( "TrainWav" ) );
		Operations op = new Operations( dir );
		op.generateCodebook( );
		op.hmmTrain( );
		assertModelsIdentical( dir );
		assertEquals( "Apple", op.hmmGetWordFromFile( new File( "TrainWav/Apple/Apple0.wav" ) ) );
	}

	@Test
	void generatingTheCodebookTwiceGivesTheSameCodebook( @TempDir Path dir ) throws Exception {
		TestFiles.copyTree( Paths.get( "TrainWav" ), dir.resolve( "TrainWav" ) );
		Path codebook = dir.resolve( "models/codeBook/codebook.cbk" );
		Operations op = new Operations( dir );
		op.generateCodebook( );
		byte[] first = Files.readAllBytes( codebook );
		op.generateCodebook( );
		assertArrayEquals( first, Files.readAllBytes( codebook ) );
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=OperationsBaseDirTest`
Expected: compilation FAILURE, `constructor Operations in class Operations cannot be applied to given types`.

- [ ] **Step 3: Implement**

`ObjectIODataBase.java`: add imports `java.nio.file.Path`, `java.nio.file.Paths`; replace the constructor and folder assignments:
```java
	/**
	 * folder that contains models/
	 */
	final Path		baseDir;

	public ObjectIODataBase( ) {
		this( Paths.get( "" ) );
	}

	/**
	 * @param baseDir
	 *            folder that contains the models/ folder
	 */
	public ObjectIODataBase( Path baseDir ) {
		this.baseDir = baseDir;
	}
```
In `setType`, replace `CURRENTFOLDER = "models" + File.separator + "HMM";` with `CURRENTFOLDER = baseDir.resolve( "models" ).resolve( "HMM" ).toString( );` and `CURRENTFOLDER = "models" + File.separator + "codeBook";` with `CURRENTFOLDER = baseDir.resolve( "models" ).resolve( "codeBook" ).toString( );` (with the default base dir these are the same relative paths as before).

`TrainingTestingWaveFiles.java`: add imports `java.nio.file.Path`, `java.nio.file.Paths`; replace the constructor:
```java
	public TrainingTestingWaveFiles( String testOrTrain ) {
		this( testOrTrain, Paths.get( "" ) );
	}

	/**
	 * @param baseDir
	 *            folder that contains TrainWav/ and TestWav/
	 */
	public TrainingTestingWaveFiles( String testOrTrain, Path baseDir ) {
		if ( testOrTrain.equalsIgnoreCase( "test" ) ) {
			setWavPath( baseDir.resolve( "TestWav" ).toFile( ) );
		} else if ( testOrTrain.equalsIgnoreCase( "train" ) ) {
			setWavPath( baseDir.resolve( "TrainWav" ).toFile( ) );
		}
	}
```

`HiddenMarkov.java`: replace the `HiddenMarkov( String word )` constructor and `save( String modelName )` with the versions below, and remove the now unused `import org.ioe.tprsa.util.ArrayWriter;` (the prints in `save` go away; they do not affect the saved model):
```java
	public HiddenMarkov( String word ) throws Exception {
		this( word, new ObjectIODataBase( ) );
	}

	/**
	 * loads the trained model of {@code word} from the given database
	 */
	public HiddenMarkov( String word, DataBase db ) throws Exception {
		db.setType( "hmm" );
		HMMModel model = ( HMMModel ) db.readModel( word );
		num_obSeq = model.getNum_obSeq( );
		output = model.getOutput( );
		transition = model.getTransition( );
		pi = model.getPi( );
		num_states = output.length;
		num_symbols = output[ 0 ].length;
	}
```
```java
	public void save( String modelName ) throws Exception {
		save( modelName, new ObjectIODataBase( ) );
	}

	/**
	 * saves the model as {@code modelName} into the given database
	 */
	public void save( String modelName, DataBase db ) throws Exception {
		db.setType( "hmm" );
		HMMModel model = new HMMModel( );
		model.setOutput( output );
		model.setPi( pi );
		model.setTransition( transition );
		db.saveModel( model, modelName );
	}
```

`Codebook.java`: replace the `Codebook()` constructor and `saveToFile()`:
```java
	public Codebook() throws Exception {
		this(new ObjectIODataBase());
	}

	/**
	 * constructor to load a saved Codebook from the given database
	 */
	public Codebook(DataBase db) throws Exception {
		db.setType("cbk");
		CodeBookDictionary cbd = (CodeBookDictionary) db.readModel(null);
		dimension = cbd.getDimension();
		centroids = cbd.getCent();
	}
```
```java
	public void saveToFile() throws Exception {
		saveToFile(new ObjectIODataBase());
	}

	/**
	 * save Codebook into the given database
	 */
	public void saveToFile(DataBase db) throws Exception {
		db.setType("cbk");
		CodeBookDictionary cbd = new CodeBookDictionary();
		// no need to save all the points,
		// must be removed in objectIO, to reduce the size of file
		for (Centroid centroid : centroids) {
			centroid.pts.removeAllElements();
		}
		cbd.setDimension(dimension);
		cbd.setCent(centroids);
		db.saveModel(cbd, null);// filepath is not used
	}
```

`Operations.java`: add imports `java.nio.file.Path`, `java.nio.file.Paths`; replace the constructor:
```java
	/**
	 * folder that contains TrainWav/ and models/
	 */
	final Path							baseDir;

	public Operations( ) {
		this( Paths.get( "" ) );
	}

	/**
	 * @param baseDir
	 *            folder that contains TrainWav/ and models/ ({@link #Operations()} uses the working directory)
	 */
	public Operations( Path baseDir ) {
		this.baseDir = baseDir;
		wd = new WaveData( );
	}
```
Then make every file access go through `baseDir`:
- both `trainTestWavs = new TrainingTestingWaveFiles( "train" );` → `trainTestWavs = new TrainingTestingWaveFiles( "train", baseDir );`
- both `cb = new Codebook( );` → `cb = new Codebook( new ObjectIODataBase( baseDir ) );`
- `cbk.saveToFile( );` → `cbk.saveToFile( new ObjectIODataBase( baseDir ) );`
- `mkv.save( currentWord );` → `mkv.save( currentWord, new ObjectIODataBase( baseDir ) );`
- both `db = new ObjectIODataBase( );` (in `hmmGetWordWithFeature` and `checkWord`) → `db = new ObjectIODataBase( baseDir );`
- `hmmModels[ i ] = new HiddenMarkov( words.get( i ) );` → `hmmModels[ i ] = new HiddenMarkov( words.get( i ), new ObjectIODataBase( baseDir ) );`
- first line of `generateCodebook()` body: insert `allFeaturesList.clear( );` (Review Focus 4).

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest=OperationsBaseDirTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Checkpoint**

Run: `mvn -q test`; Expected: all tests pass (existing 76 + 2).

---

### Task 2: Pre-processing trace

**Files:**
- Create: `src/org/ioe/tprsa/trace/PreprocessTrace.java`
- Modify: `src/org/ioe/tprsa/audio/preProcessings/EndPointDetection.java`, `src/org/ioe/tprsa/audio/PreProcess.java`
- Test: `test/org/ioe/tprsa/audio/PreprocessTraceTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `record PreprocessTrace( int sampleRate, float[] normalisedSignal, double noiseMean, double noiseSd, double voicedThreshold, int noiseSamples, int epdFrameSize, boolean[] voicedFrames, boolean wholeSignalUsed, float[] trimmedSignal, int frameSize, int hop, float preEmphasis, float[] hammingWindow, float[][] rawFrames, float[][] preEmphasisedFrames, float[][] windowedFrames )` with `durationSec()`, `keptFraction()`, `frameCount()`; `PreProcess.toTrace()`; `PreProcess.preEmphasisedFrames` (public field); EPD getters `getNoiseMean()`, `getNoiseSd()`, `getVoicedFrames()`, `isWholeSignalUsed()`, `getVoicedThreshold()`, `getFrameSize()`, `getNoiseSamples()`.

- [ ] **Step 1: Write the failing test**

`test/org/ioe/tprsa/audio/PreprocessTraceTest.java`:
```java
package org.ioe.tprsa.audio;

import org.ioe.tprsa.TestSignals;
import org.ioe.tprsa.trace.PreprocessTrace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreprocessTraceTest {

	private static final int RATE = TestSignals.SAMPLING_RATE;
	private static final int SPF = 512;

	@Test
	void recordsEndPointDetectionAndFraming( ) {
		float[] signal = TestSignals.silenceToneSilence( RATE / 2, RATE / 2, 1 );
		PreProcess p = new PreProcess( signal, SPF, RATE );
		PreprocessTrace t = p.toTrace( );

		assertEquals( RATE, t.sampleRate( ) );
		assertEquals( signal.length, t.normalisedSignal( ).length );
		assertEquals( 1.5, t.durationSec( ), 1e-3 );
		assertEquals( RATE / 100, t.epdFrameSize( ) );
		assertEquals( RATE / 5, t.noiseSamples( ) );
		assertEquals( 3.0, t.voicedThreshold( ) );
		assertFalse( t.wholeSignalUsed( ) );
		assertTrue( t.noiseSd( ) > 0 && t.noiseSd( ) < 0.05, "noise sd " + t.noiseSd( ) );
		assertEquals( signal.length / ( RATE / 100 ), t.voicedFrames( ).length );
		int voiced = 0;
		for ( boolean v : t.voicedFrames( ) ) {
			voiced += v ? 1 : 0;
		}
		assertEquals( t.voicedFrames( ).length / 3.0, voiced, 3, "the tone is the middle third" );
		assertArrayEquals( p.afterEndPtDetection, t.trimmedSignal( ) );
		assertEquals( 1.0 / 3, t.keptFraction( ), 0.02 );

		assertEquals( p.noOfFrames, t.frameCount( ) );
		assertEquals( SPF, t.frameSize( ) );
		assertEquals( SPF / 2, t.hop( ) );
		assertEquals( SPF, t.hammingWindow( ).length );
		assertEquals( p.hammingWindow[ 1 ], t.hammingWindow( )[ 0 ] );
		assertArrayEquals( p.rawFramedSignal[ 0 ], t.rawFrames( )[ 0 ] );
		assertArrayEquals( p.framedSignal[ 0 ], t.windowedFrames( )[ 0 ] );
		float[] raw = t.rawFrames( )[ 0 ], pre = t.preEmphasisedFrames( )[ 0 ];
		assertEquals( raw[ 5 ] - t.preEmphasis( ) * raw[ 4 ], pre[ 5 ], 1e-6, "pre-emphasis only, before the window" );
	}

	@Test
	void silentInputUsesTheWholeSignal( ) {
		PreprocessTrace t = new PreProcess( new float[ RATE / 2 ], SPF, RATE ).toTrace( );
		assertTrue( t.wholeSignalUsed( ) );
		assertEquals( RATE / 2, t.trimmedSignal( ).length );
	}

	@Test
	void inputShorterThanOneEndPointFrameStillGivesOneFrame( ) {
		PreprocessTrace t = new PreProcess( TestSignals.sine( 100, 440, 1000 ), SPF, RATE ).toTrace( );
		assertTrue( t.wholeSignalUsed( ) );
		assertEquals( 1, t.frameCount( ) );
	}

	@Test
	void traceIsACopy( ) {
		PreProcess p = new PreProcess( TestSignals.silenceToneSilence( RATE / 2, RATE / 2, 2 ), SPF, RATE );
		float before = p.framedSignal[ 0 ][ 0 ];
		p.toTrace( ).windowedFrames( )[ 0 ][ 0 ] = 99;
		assertEquals( before, p.framedSignal[ 0 ][ 0 ] );
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=PreprocessTraceTest`
Expected: compilation FAILURE, `package org.ioe.tprsa.trace does not exist`.

- [ ] **Step 3: Implement**

`src/org/ioe/tprsa/trace/PreprocessTrace.java`:
```java
package org.ioe.tprsa.trace;

/**
 * what pre-processing did to one recording: normalisation, end point detection, framing, pre-emphasis, windowing
 *
 * @param normalisedSignal
 *            the recording divided by its peak amplitude
 * @param voicedFrames
 *            end point detection decision per frame of {@code epdFrameSize} samples
 * @param wholeSignalUsed
 *            true when no speech was detected and the whole signal was kept
 * @param trimmedSignal
 *            the signal after silence removal
 */
public record PreprocessTrace( int sampleRate, float[] normalisedSignal, double noiseMean, double noiseSd, double voicedThreshold,
		int noiseSamples, int epdFrameSize, boolean[] voicedFrames, boolean wholeSignalUsed, float[] trimmedSignal, int frameSize,
		int hop, float preEmphasis, float[] hammingWindow, float[][] rawFrames, float[][] preEmphasisedFrames, float[][] windowedFrames ) {

	public double durationSec( ) {
		return normalisedSignal.length / ( double ) sampleRate;
	}

	/** fraction of the signal kept by end point detection */
	public double keptFraction( ) {
		return normalisedSignal.length == 0 ? 0 : trimmedSignal.length / ( double ) normalisedSignal.length;
	}

	public int frameCount( ) {
		return windowedFrames.length;
	}
}
```

`EndPointDetection.java` — add fields and getters after the existing `private final int samplePerFrame;`:
```java
	private double noiseMean = Double.NaN;
	private double noiseSd = Double.NaN;
	private boolean[] voicedFrames = new boolean[0];
	private boolean wholeSignalUsed;

	public double getNoiseMean() {
		return noiseMean;
	}

	public double getNoiseSd() {
		return noiseSd;
	}

	/** voiced / silence decision per frame of {@link #getFrameSize()} samples */
	public boolean[] getVoicedFrames() {
		return voicedFrames.clone();
	}

	/** true when no speech was detected and the whole signal is returned */
	public boolean isWholeSignalUsed() {
		return wholeSignalUsed;
	}

	public double getVoicedThreshold() {
		return VOICED_THRESHOLD;
	}

	public int getFrameSize() {
		return samplePerFrame;
	}

	public int getNoiseSamples() {
		return firstSamples;
	}
```
In `doEndPointDetection()`:
- after `sd = Math.sqrt(sum / firstSamples);` add `noiseMean = m;` and `noiseSd = sd;`
- in the block `if (sd == 0 || Double.isNaN(sd)) {` add `wholeSignalUsed = true;` before `return originalSignal.clone();`
- directly before `if (usefulFramesCount == 0) {` add
```java
		voicedFrames = new boolean[frameCount];
		for (int i = 0; i < frameCount; i++) {
			voicedFrames[i] = voicedFrame[i] == 1;
		}
```
- in the block `if (usefulFramesCount == 0) {` add `wholeSignalUsed = true;` before `return originalSignal.clone();`

`PreProcess.java` — add `import java.util.Arrays;` and `import org.ioe.tprsa.trace.PreprocessTrace;`; add the field after `rawFramedSignal`:
```java
	/**
	 * frames after pre-emphasis, before windowing
	 */
	public float[][]	preEmphasisedFrames;
```
Replace in the constructor
```java
		rawFramedSignal = new float[ noOfFrames ][ ];
		for ( int i = 0; i < noOfFrames; i++ ) {
			rawFramedSignal[ i ] = framedSignal[ i ].clone( );
		}
		doPreEmphasis( );
		doWindowing( );
```
with
```java
		rawFramedSignal = copy( framedSignal );
		doPreEmphasis( );
		preEmphasisedFrames = copy( framedSignal );
		doWindowing( );
```
and add the methods:
```java
	private static float[][] copy( float[][] frames ) {
		float[][] c = new float[ frames.length ][ ];
		for ( int i = 0; i < frames.length; i++ ) {
			c[ i ] = frames[ i ].clone( );
		}
		return c;
	}

	/**
	 * everything this pre-processing computed, as an independent copy
	 */
	public PreprocessTrace toTrace( ) {
		return new PreprocessTrace( samplingRate, originalSignal.clone( ), epd.getNoiseMean( ), epd.getNoiseSd( ), epd.getVoicedThreshold( ),
				epd.getNoiseSamples( ), epd.getFrameSize( ), epd.getVoicedFrames( ), epd.isWholeSignalUsed( ), afterEndPtDetection.clone( ),
				samplePerFrame, samplePerFrame / 2, PRE_EMPHASIS, Arrays.copyOfRange( hammingWindow, 1, samplePerFrame + 1 ),
				copy( rawFramedSignal ), copy( preEmphasisedFrames ), copy( framedSignal ) );
	}
```

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest=PreprocessTraceTest`; Expected: PASS (4 tests).

- [ ] **Step 5: Checkpoint**

Run: `mvn -q test`; Expected: all pass.

---

### Task 3: Feature extraction trace

**Files:**
- Create: `src/org/ioe/tprsa/trace/MfccFrame.java`, `src/org/ioe/tprsa/trace/FeatureTrace.java`
- Modify: `src/org/ioe/tprsa/audio/feature/MFCC.java`, `src/org/ioe/tprsa/audio/FeatureExtract.java`
- Test: `test/org/ioe/tprsa/audio/FeatureTraceTest.java`

**Interfaces:**
- Produces: `record MfccFrame( double[] magnitudeSpectrum, double[] logMelEnergies, double[] cepstra )`; `MFCC.computeFrame( float[] frame ) : MfccFrame`; `MFCC.getMelCentreBins() : int[]` (32 entries); `record FeatureTrace( double[][] magnitudeSpectra, double[][] logMelEnergies, double[][] mfccBeforeCmn, double[][] mfcc, double[][] deltaMfcc, double[][] deltaDeltaMfcc, double[] logEnergy, double[] deltaLogEnergy, double[] deltaDeltaLogEnergy, double[][] featureVectors, int[] melCentreBins )` with `frameCount()`; `FeatureExtract.toTrace()` (throws `IllegalStateException` before `makeMfccFeatureVector()`).

- [ ] **Step 1: Write the failing test**

`test/org/ioe/tprsa/audio/FeatureTraceTest.java`:
```java
package org.ioe.tprsa.audio;

import org.ioe.tprsa.TestSignals;
import org.ioe.tprsa.audio.feature.MFCC;
import org.ioe.tprsa.trace.FeatureTrace;
import org.ioe.tprsa.trace.MfccFrame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class FeatureTraceTest {

	private static final int RATE = TestSignals.SAMPLING_RATE;
	private static final int SPF = 512;

	private static FeatureExtract extract( ) {
		PreProcess p = new PreProcess( TestSignals.silenceToneSilence( RATE / 2, RATE / 2, 4 ), SPF, RATE );
		FeatureExtract fe = new FeatureExtract( p.framedSignal, p.rawFramedSignal, RATE, SPF );
		fe.makeMfccFeatureVector( );
		return fe;
	}

	@Test
	void recordsEveryIntermediateArray( ) {
		FeatureExtract fe = extract( );
		FeatureTrace t = fe.toTrace( );
		int n = t.frameCount( );
		assertEquals( fe.getFeatureVector( ).getNoOfFrames( ), n );
		assertEquals( SPF / 2 + 1, t.magnitudeSpectra( )[ 0 ].length );
		assertEquals( 30, t.logMelEnergies( )[ 0 ].length );
		for ( double[][] m : new double[][][] { t.mfccBeforeCmn( ), t.mfcc( ), t.deltaMfcc( ), t.deltaDeltaMfcc( ) } ) {
			assertEquals( n, m.length );
			assertEquals( 12, m[ 0 ].length );
		}
		assertEquals( n, t.logEnergy( ).length );
		assertEquals( n, t.deltaLogEnergy( ).length );
		assertEquals( n, t.deltaDeltaLogEnergy( ).length );
		assertArrayEquals( fe.getFeatureVector( ).getFeatureVector( )[ 3 ], t.featureVectors( )[ 3 ] );
		assertArrayEquals( t.deltaMfcc( )[ 3 ], Arrays.copyOfRange( t.featureVectors( )[ 3 ], 12, 24 ) );
		assertEquals( t.logEnergy( )[ 3 ], t.featureVectors( )[ 3 ][ 36 ] );
		assertEquals( 32, t.melCentreBins( ).length );
	}

	@Test
	void mfccIsTheMeanNormalisedRawCepstrum( ) {
		FeatureTrace t = extract( ).toTrace( );
		for ( int c = 0; c < 12; c++ ) {
			double mean = 0;
			for ( double[] frame : t.mfccBeforeCmn( ) ) {
				mean += frame[ c ];
			}
			mean /= t.frameCount( );
			for ( int f = 0; f < t.frameCount( ); f++ ) {
				assertEquals( t.mfccBeforeCmn( )[ f ][ c ] - mean, t.mfcc( )[ f ][ c ], 1e-9 );
			}
		}
	}

	@Test
	void computeFrameMatchesDoMfcc( ) {
		MFCC mfcc = new MFCC( SPF, RATE, 12 );
		float[] frame = TestSignals.sine( SPF, 1000, 0.5 );
		MfccFrame f = mfcc.computeFrame( frame );
		assertArrayEquals( mfcc.doMFCC( frame ), f.cepstra( ), 0 );
		assertEquals( SPF / 2 + 1, f.magnitudeSpectrum( ).length );
		int peak = 0;
		for ( int k = 1; k < f.magnitudeSpectrum( ).length; k++ ) {
			peak = f.magnitudeSpectrum( )[ k ] > f.magnitudeSpectrum( )[ peak ] ? k : peak;
		}
		assertEquals( 1000.0 * SPF / RATE, peak, 1, "spectrum peak at 1000 Hz" );
	}

	@Test
	void toTraceBeforeExtractionFails( ) {
		PreProcess p = new PreProcess( TestSignals.silenceToneSilence( RATE / 2, RATE / 2, 5 ), SPF, RATE );
		FeatureExtract fe = new FeatureExtract( p.framedSignal, p.rawFramedSignal, RATE, SPF );
		assertThrows( IllegalStateException.class, fe::toTrace );
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=FeatureTraceTest`; Expected: compilation FAILURE, `cannot find symbol class FeatureTrace`.

- [ ] **Step 3: Implement**

`src/org/ioe/tprsa/trace/MfccFrame.java`:
```java
package org.ioe.tprsa.trace;

/**
 * MFCC intermediates of one frame
 *
 * @param magnitudeSpectrum
 *            |X(k)| for k = 0..N/2
 * @param logMelEnergies
 *            log of the 30 mel filter bank outputs
 * @param cepstra
 *            the 12 MFCCs (before cepstral mean normalisation)
 */
public record MfccFrame( double[] magnitudeSpectrum, double[] logMelEnergies, double[] cepstra ) {
}
```

`src/org/ioe/tprsa/trace/FeatureTrace.java`:
```java
package org.ioe.tprsa.trace;

/**
 * per frame feature extraction intermediates of one recording; every 2-D array is [frame][coefficient]
 *
 * @param melCentreBins
 *            FFT bin of each mel filter edge / centre (32 entries for 30 filters)
 */
public record FeatureTrace( double[][] magnitudeSpectra, double[][] logMelEnergies, double[][] mfccBeforeCmn, double[][] mfcc,
		double[][] deltaMfcc, double[][] deltaDeltaMfcc, double[] logEnergy, double[] deltaLogEnergy, double[] deltaDeltaLogEnergy,
		double[][] featureVectors, int[] melCentreBins ) {

	public int frameCount( ) {
		return featureVectors.length;
	}
}
```

`MFCC.java`: add `import java.util.Arrays;` and `import org.ioe.tprsa.trace.MfccFrame;`. Replace the whole `doMFCC` method (from `public double[] doMFCC(float[] framedSignal) {` to its closing brace before `private double[] magnitudeSpectrum`) with:
```java
	public double[] doMFCC(float[] framedSignal) {
		return computeFrame(framedSignal).cepstra();
	}

	/**
	 * MFCC of one (pre-emphasised, windowed) frame, keeping the intermediate results
	 */
	public MfccFrame computeFrame(float[] framedSignal) {
		// Magnitude Spectrum (pre-emphasis and windowing are done in PreProcess)
		double[] bin = magnitudeSpectrum(framedSignal);
		// process Mel Filterbank
		double[] fbank = melFilter(bin, fftBinIndices());
		// Non-linear transformation
		double[] f = nonLinearTransformation(fbank);
		// Cepstral coefficients, by DCT
		double[] cepc = dct.performDCT(f);
		return new MfccFrame(Arrays.copyOf(bin, samplePerFrame / 2 + 1), f, cepc);
	}

	/**
	 * FFT bins of the mel filter edges / centres: filter k (1..30) rises from bin[k-1] to bin[k] and falls to bin[k+1]
	 */
	public int[] getMelCentreBins() {
		return fftBinIndices();
	}
```

`FeatureExtract.java`: add imports `org.ioe.tprsa.trace.FeatureTrace`, `org.ioe.tprsa.trace.MfccFrame`. Add fields after `private final double[][] mfccFeature;`:
```java
	private final double[][]		magnitudeSpectra;
	private final double[][]		logMelEnergies;
	private final double[][]		mfccBeforeCmn;
```
In the 4-argument constructor, after `mfccFeature = new double[ noOfFrames ][ numCepstra ];` add:
```java
		magnitudeSpectra = new double[ noOfFrames ][ ];
		logMelEnergies = new double[ noOfFrames ][ ];
		mfccBeforeCmn = new double[ noOfFrames ][ ];
```
Replace the body of `calculateMFCC()`:
```java
	private void calculateMFCC( ) {
		for ( int i = 0; i < noOfFrames; i++ ) {
			// for each frame i, make mfcc from current framed signal
			MfccFrame frame = mfcc.computeFrame( framedSignal[ i ] );
			magnitudeSpectra[ i ] = frame.magnitudeSpectrum( );
			logMelEnergies[ i ] = frame.logMelEnergies( );
			mfccFeature[ i ] = frame.cepstra( );
			mfccBeforeCmn[ i ] = frame.cepstra( ).clone( );
		}
	}
```
Add:
```java
	/**
	 * all intermediates of {@link #makeMfccFeatureVector()}
	 */
	public FeatureTrace toTrace( ) {
		if ( fv.getFeatureVector( ) == null ) {
			throw new IllegalStateException( "call makeMfccFeatureVector() first" );
		}
		return new FeatureTrace( magnitudeSpectra, logMelEnergies, mfccBeforeCmn, mfccFeature, deltaMfcc, deltaDeltaMfcc, energyVal,
				deltaEnergy, deltaDeltaEnergy, featureVector, mfcc.getMelCentreBins( ) );
	}
```

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest=FeatureTraceTest`; Expected: PASS (4 tests).

- [ ] **Step 5: Checkpoint**

Run: `mvn -q test`; Expected: all pass.

---

### Task 4: VQ and Viterbi traces

**Files:**
- Create: `src/org/ioe/tprsa/trace/VqTrace.java`, `src/org/ioe/tprsa/trace/WordScore.java`
- Modify: `src/org/ioe/tprsa/classify/speech/vq/Codebook.java`, `src/org/ioe/tprsa/classify/speech/HiddenMarkov.java`
- Test: extend `test/org/ioe/tprsa/classify/speech/vq/CodebookTest.java`, `test/org/ioe/tprsa/classify/speech/HiddenMarkovTest.java`

**Interfaces:**
- Produces: `record VqTrace( int codebookSize, int[] codewords, double[] distances )` with `meanDistance()`, `distinctCodewords()`; `Codebook.quantizeWithTrace( Points[] ) : VqTrace`; `Codebook.size() : int`; `record WordScore( String word, double score, int[] statePath, double[][] viterbiGrid )`; `HiddenMarkov.getViterbiGrid() : double[][]` ([frame][state], copy of the last `viterbi` call); `HiddenMarkov.getStatePath() : int[]`.

- [ ] **Step 1: Write the failing tests**

Add to `CodebookTest.java`:
```java
	@Test
	void quantizeWithTraceRecordsCodewordsAndDistances( ) {
		Points[] pts = clusters( new double[][] { { 1, 2 }, { 4, 9 }, { 8, 3 }, { 12, 12 } }, 20, 0.5, 6 );
		Codebook cb = new Codebook( pts, 4 );
		org.ioe.tprsa.trace.VqTrace t = cb.quantizeWithTrace( pts );
		assertEquals( 4, t.codebookSize( ) );
		assertEquals( 4, cb.size( ) );
		assertArrayEquals( cb.quantize( pts ), t.codewords( ) );
		double sum = 0;
		for ( int i = 0; i < pts.length; i++ ) {
			double[] c = cb.centroids[ t.codewords( )[ i ] ].getAllCo( );
			double d = Math.hypot( pts[ i ].getCo( 0 ) - c[ 0 ], pts[ i ].getCo( 1 ) - c[ 1 ] );
			assertEquals( d, t.distances( )[ i ], 1e-12 );
			sum += d;
		}
		assertEquals( sum / pts.length, t.meanDistance( ), 1e-12 );
		assertEquals( 4, t.distinctCodewords( ) );
	}
```
Add to `HiddenMarkovTest.java`:
```java
	@Test
	void viterbiKeepsItsScoreGridAndPath( ) {
		HiddenMarkov hmm = model( new double[] { 1, 0, 0 } );
		int[] obs = { 0, 0, 1, 1, 2, 2, 1 };
		double score = hmm.viterbi( obs );
		double[][] grid = hmm.getViterbiGrid( );
		assertEquals( obs.length, grid.length );
		assertEquals( 3, grid[ 0 ].length );
		double best = Double.NEGATIVE_INFINITY;
		for ( double v : grid[ obs.length - 1 ] ) {
			best = Math.max( best, v );
		}
		assertEquals( score, best, 1e-12 );
		int[] path = hmm.getStatePath( );
		assertEquals( obs.length, path.length );
		assertEquals( 0, path[ 0 ] );
		for ( int t = 1; t < path.length; t++ ) {
			assertTrue( path[ t ] >= path[ t - 1 ] && path[ t ] - path[ t - 1 ] <= 2, "left-to-right, skips at most one state" );
		}
		grid[ 0 ][ 0 ] = 42;
		assertNotEquals( 42, hmm.getViterbiGrid( )[ 0 ][ 0 ], "returns a copy" );
	}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest='CodebookTest,HiddenMarkovTest'`; Expected: compilation FAILURE, `cannot find symbol method quantizeWithTrace`.

- [ ] **Step 3: Implement**

`src/org/ioe/tprsa/trace/VqTrace.java`:
```java
package org.ioe.tprsa.trace;

import java.util.Arrays;

/**
 * vector quantization of one recording
 *
 * @param codewords
 *            index of the nearest codeword per frame
 * @param distances
 *            Euclidean distance of each frame's feature vector to that codeword
 */
public record VqTrace( int codebookSize, int[] codewords, double[] distances ) {

	public double meanDistance( ) {
		return Arrays.stream( distances ).average( ).orElse( 0 );
	}

	public int distinctCodewords( ) {
		return ( int ) Arrays.stream( codewords ).distinct( ).count( );
	}
}
```

`src/org/ioe/tprsa/trace/WordScore.java`:
```java
package org.ioe.tprsa.trace;

/**
 * how one word's HMM scored a recording
 *
 * @param score
 *            Viterbi log probability of the best state path
 * @param statePath
 *            best state per frame
 * @param viterbiGrid
 *            best log score of any path ending in each state, [frame][state]
 */
public record WordScore( String word, double score, int[] statePath, double[][] viterbiGrid ) {
}
```

`Codebook.java`: add `import org.ioe.tprsa.trace.VqTrace;` and, after `quantize`:
```java
	/**
	 * like {@link #quantize(Points[])}, also recording the distance of each point to its codeword
	 */
	public VqTrace quantizeWithTrace(Points[] pts) {
		int[] output = new int[pts.length];
		double[] distances = new double[pts.length];
		for (int i = 0; i < pts.length; i++) {
			output[i] = closestCentroidToPoint(pts[i]);
			distances[i] = getDistance(pts[i], centroids[output[i]]);
		}
		return new VqTrace(centroids.length, output, distances);
	}

	/**
	 * number of codewords
	 */
	public int size() {
		return centroids.length;
	}
```

`HiddenMarkov.java`: add a field after `public int[] q;`:
```java
	/**
	 * score grid of the last {@link #viterbi(int[])} call, [frame][state]
	 */
	private double[][] lastViterbiGrid;
```
In `viterbi`, directly before the final `return max;` add `lastViterbiGrid = phi;`. Add:
```java
	/**
	 * @return copy of the Viterbi score grid of the last {@link #viterbi(int[])} call, [frame][state]
	 */
	public double[][] getViterbiGrid( ) {
		double[][] copy = new double[ lastViterbiGrid.length ][ ];
		for ( int t = 0; t < copy.length; t++ ) {
			copy[ t ] = lastViterbiGrid[ t ].clone( );
		}
		return copy;
	}

	/**
	 * @return best state path of the last {@link #viterbi(int[])} call
	 */
	public int[] getStatePath( ) {
		return q.clone( );
	}
```

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest='CodebookTest,HiddenMarkovTest'`; Expected: PASS.

- [ ] **Step 5: Checkpoint**

Run: `mvn -q test`; Expected: all pass.

---

### Task 5: Recognition trace in `Operations`

**Files:**
- Create: `src/org/ioe/tprsa/trace/RecognitionTrace.java`
- Modify: `src/org/ioe/tprsa/mediator/Operations.java`
- Test: `test/org/ioe/tprsa/mediator/OperationsRecognitionTraceTest.java`

**Interfaces:**
- Consumes: `PreProcess.toTrace()`, `FeatureExtract.toTrace()`, `Codebook.quantizeWithTrace()`, `HiddenMarkov.getStatePath()/getViterbiGrid()`, `new Operations( Path )`.
- Produces: `record RecognitionTrace( String source, PreprocessTrace preprocess, FeatureTrace features, VqTrace vq, List<WordScore> scores, String recognizedWord, String expectedWord )` with `isVerification()`, `verified()`, `margin()`, `scoreOf( String ) : Optional<WordScore>`, `rankOf( String ) : int` (1-based, 0 if absent); `Operations.recognizeWithTrace( float[] samples, String expectedWord )`, `Operations.recognizeWithTrace( File wav, String expectedWord )` (expectedWord may be null). Errors: `IllegalStateException` starting with `"Train first"`, `IllegalArgumentException` containing `"expected 16-bit mono 22050 Hz"`.

- [ ] **Step 1: Write the failing test**

`test/org/ioe/tprsa/mediator/OperationsRecognitionTraceTest.java`:
```java
package org.ioe.tprsa.mediator;

import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.trace.WordScore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag( "integration" )
class OperationsRecognitionTraceTest {

	private static File[] trainingFiles( ) {
		File[] dirs = new File( "TrainWav" ).listFiles( File::isDirectory );
		Arrays.sort( dirs );
		return Arrays.stream( dirs ).flatMap( d -> {
			File[] f = d.listFiles( ( x, n ) -> n.endsWith( ".wav" ) );
			Arrays.sort( f );
			return Arrays.stream( f );
		} ).toArray( File[]::new );
	}

	/**
	 * the results recorded before the refactoring: every recording is recognised as its folder's word except
	 * Ship3.wav, which the committed models hear as Zebra. Update this if models/ is retrained.
	 */
	@Test
	void recognitionResultsAreUnchanged( ) throws Exception {
		Operations op = new Operations( );
		for ( File f : trainingFiles( ) ) {
			String expected = f.getName( ).equals( "Ship3.wav" ) ? "Zebra" : f.getParentFile( ).getName( );
			assertEquals( expected, op.recognizeWithTrace( f, null ).recognizedWord( ), f.getName( ) );
			assertEquals( expected, op.hmmGetWordFromFile( f ), f.getName( ) );
		}
	}

	@Test
	void traceStagesAreConsistent( ) throws Exception {
		Operations op = new Operations( );
		RecognitionTrace t = op.recognizeWithTrace( new File( "TrainWav/Hello/Hello2.wav" ), null );
		assertEquals( "Hello2.wav", t.source( ) );
		assertFalse( t.isVerification( ) );

		int frames = t.features( ).frameCount( );
		assertEquals( t.preprocess( ).frameCount( ), frames );
		assertEquals( 39, t.features( ).featureVectors( )[ 0 ].length );
		assertEquals( frames, t.vq( ).codewords( ).length );
		assertEquals( 256, t.vq( ).codebookSize( ) );
		for ( int c : t.vq( ).codewords( ) ) {
			assertTrue( c >= 0 && c < 256 );
		}

		List< String > registered = List.of( "Apple", "Developer", "Hello", "Ship", "Zebra" );
		assertEquals( registered, t.scores( ).stream( ).map( WordScore::word ).sorted( ).toList( ) );
		for ( int i = 1; i < t.scores( ).size( ); i++ ) {
			assertTrue( t.scores( ).get( i - 1 ).score( ) >= t.scores( ).get( i ).score( ), "sorted best first" );
		}
		assertEquals( t.scores( ).get( 0 ).word( ), t.recognizedWord( ) );
		for ( WordScore s : t.scores( ) ) {
			assertEquals( frames, s.statePath( ).length );
			assertEquals( frames, s.viterbiGrid( ).length );
			double best = Arrays.stream( s.viterbiGrid( )[ frames - 1 ] ).max( ).orElseThrow( );
			assertEquals( s.score( ), best, 1e-9 );
		}
		assertTrue( t.margin( ) >= 0 );
	}

	@Test
	void verificationComparesIgnoringCase( ) throws Exception {
		RecognitionTrace t = new Operations( ).recognizeWithTrace( new File( "TrainWav/Apple/Apple0.wav" ), "apple" );
		assertTrue( t.isVerification( ) );
		assertTrue( t.verified( ) );
		assertEquals( 1, t.rankOf( "Apple" ) );
		RecognitionTrace miss = new Operations( ).recognizeWithTrace( new File( "TrainWav/Ship/Ship3.wav" ), "Ship" );
		assertFalse( miss.verified( ) );
		assertTrue( miss.rankOf( "Ship" ) > 1 );
		assertTrue( miss.scoreOf( "ship" ).isPresent( ) );
	}

	@Test
	void recognizingWithoutModelsAsksToTrainFirst( @TempDir Path dir ) {
		IllegalStateException e = assertThrows( IllegalStateException.class,
				( ) -> new Operations( dir ).recognizeWithTrace( new float[ 11025 ], null ) );
		assertTrue( e.getMessage( ).startsWith( "Train first" ), e.getMessage( ) );
	}

	@Test
	void wavWithAnotherSampleRateIsRejected( @TempDir Path dir ) throws Exception {
		File wav = dir.resolve( "8k.wav" ).toFile( );
		byte[] pcm = new byte[ 8000 * 2 ];
		AudioFormat fmt = new AudioFormat( 8000f, 16, 1, true, false );
		AudioSystem.write( new AudioInputStream( new ByteArrayInputStream( pcm ), fmt, 8000 ), AudioFileFormat.Type.WAVE, wav );
		IllegalArgumentException e = assertThrows( IllegalArgumentException.class, ( ) -> new Operations( ).recognizeWithTrace( wav, null ) );
		assertTrue( e.getMessage( ).contains( "expected 16-bit mono 22050 Hz" ), e.getMessage( ) );
		assertTrue( e.getMessage( ).contains( "8000" ), e.getMessage( ) );
	}

	@Test
	void silentRecordingStillGivesATrace( ) throws Exception {
		RecognitionTrace t = new Operations( ).recognizeWithTrace( new float[ 11025 ], null );
		assertTrue( t.preprocess( ).wholeSignalUsed( ) );
		assertTrue( t.features( ).frameCount( ) > 0 );
		assertNotNull( t.recognizedWord( ) );
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=OperationsRecognitionTraceTest`; Expected: compilation FAILURE, `cannot find symbol method recognizeWithTrace`.

- [ ] **Step 3: Implement**

`src/org/ioe/tprsa/trace/RecognitionTrace.java`:
```java
package org.ioe.tprsa.trace;

import java.util.List;
import java.util.Optional;

/**
 * everything computed while recognising (or verifying) one recording
 *
 * @param source
 *            file name, or "recording" for captured audio
 * @param scores
 *            every word model's score, best first
 * @param expectedWord
 *            the word being verified, or null for plain recognition
 */
public record RecognitionTrace( String source, PreprocessTrace preprocess, FeatureTrace features, VqTrace vq, List< WordScore > scores,
		String recognizedWord, String expectedWord ) {

	public RecognitionTrace {
		scores = List.copyOf( scores );
	}

	public boolean isVerification( ) {
		return expectedWord != null;
	}

	/** verification succeeded: the recognised word is the expected one (ignoring case) */
	public boolean verified( ) {
		return expectedWord != null && expectedWord.equalsIgnoreCase( recognizedWord );
	}

	/** score difference between the best and the second best word */
	public double margin( ) {
		return scores.size( ) < 2 ? Double.POSITIVE_INFINITY : scores.get( 0 ).score( ) - scores.get( 1 ).score( );
	}

	public Optional< WordScore > scoreOf( String word ) {
		return scores.stream( ).filter( s -> s.word( ).equalsIgnoreCase( word ) ).findFirst( );
	}

	/** 1-based rank of the word, 0 when it has no model */
	public int rankOf( String word ) {
		for ( int i = 0; i < scores.size( ); i++ ) {
			if ( scores.get( i ).word( ).equalsIgnoreCase( word ) ) {
				return i + 1;
			}
		}
		return 0;
	}
}
```

`Operations.java`: add imports `javax.sound.sampled.AudioFormat`, `java.util.Comparator`, `org.ioe.tprsa.trace.RecognitionTrace`, `org.ioe.tprsa.trace.VqTrace`, `org.ioe.tprsa.trace.WordScore`. Replace the methods `hmmGetWordFromFile`, `hmmGetWordFromAmplitureArray` and `hmmGetWordWithFeature` with:
```java
	public String hmmGetWordFromFile( File speechFile ) throws Exception {
		return recognizeWithTrace( speechFile, null ).recognizedWord( );
	}

	public String hmmGetWordFromAmplitureArray( float[] byteArray ) throws Exception {
		return recognizeWithTrace( byteArray, null ).recognizedWord( );
	}

	public String hmmGetWordWithFeature( FeatureVector feature ) throws Exception {
		List< String > registered = requireTrainedModels( );
		cb = new Codebook( new ObjectIODataBase( baseDir ) );
		return scoreWords( registered, cb.quantizeWithTrace( getPointsFromFeatureVector( feature ) ).codewords( ) ).get( 0 ).word( );
	}

	/**
	 * recognise captured audio, keeping every intermediate result
	 *
	 * @param expectedWord
	 *            word being verified, or null
	 */
	public RecognitionTrace recognizeWithTrace( float[] samples, String expectedWord ) throws Exception {
		return recognize( "recording", samples, expectedWord );
	}

	/**
	 * recognise a 16-bit mono 22050 Hz WAV file, keeping every intermediate result
	 *
	 * @param expectedWord
	 *            word being verified, or null
	 */
	public RecognitionTrace recognizeWithTrace( File wav, String expectedWord ) throws Exception {
		float[] samples = wd.extractAmplitudeFromFile( wav );
		checkFormat( wd.getFormat( ), wav.getName( ) );
		return recognize( wav.getName( ), samples, expectedWord );
	}

	private RecognitionTrace recognize( String source, float[] samples, String expectedWord ) throws Exception {
		List< String > registered = requireTrainedModels( );
		prp = new PreProcess( samples, samplePerFrame, samplingRate );
		fExt = new FeatureExtract( prp.framedSignal, prp.rawFramedSignal, samplingRate, samplePerFrame );
		fExt.makeMfccFeatureVector( );
		cb = new Codebook( new ObjectIODataBase( baseDir ) );
		VqTrace vq = cb.quantizeWithTrace( getPointsFromFeatureVector( fExt.getFeatureVector( ) ) );
		List< WordScore > scores = scoreWords( registered, vq.codewords( ) );
		return new RecognitionTrace( source, prp.toTrace( ), fExt.toTrace( ), vq, scores, scores.get( 0 ).word( ), expectedWord );
	}

	/**
	 * Viterbi score of every word model, best first (ties keep the alphabetical order, like before)
	 */
	private List< WordScore > scoreWords( List< String > registered, int[] quantized ) throws Exception {
		List< WordScore > scores = new ArrayList<>( );
		for ( String word : registered ) {
			HiddenMarkov hmm = new HiddenMarkov( word, new ObjectIODataBase( baseDir ) );
			double score = hmm.viterbi( quantized );
			scores.add( new WordScore( word, score, hmm.getStatePath( ), hmm.getViterbiGrid( ) ) );
		}
		scores.sort( Comparator.comparingDouble( WordScore::score ).reversed( ) );
		return scores;
	}

	/**
	 * @return the registered words
	 * @throws IllegalStateException
	 *             when the codebook or the word models have not been trained yet
	 */
	private List< String > requireTrainedModels( ) {
		File codebookFile = baseDir.resolve( "models" ).resolve( "codeBook" ).resolve( "codebook.cbk" ).toFile( );
		if ( !codebookFile.isFile( ) ) {
			throw new IllegalStateException( "Train first: no codebook found (" + codebookFile + ")" );
		}
		db = new ObjectIODataBase( baseDir );
		db.setType( "hmm" );
		List< String > registered = db.readRegistered( );
		if ( registered.isEmpty( ) ) {
			throw new IllegalStateException( "Train first: no HMM models found in " + baseDir.resolve( "models" ).resolve( "HMM" ) );
		}
		return registered;
	}

	private void checkFormat( AudioFormat format, String name ) {
		if ( format.getSampleRate( ) != samplingRate || format.getChannels( ) != 1 || format.getSampleSizeInBits( ) != 16 ) {
			throw new IllegalArgumentException( name + ": expected 16-bit mono " + samplingRate + " Hz, got " + format.getSampleSizeInBits( )
					+ "-bit, " + format.getChannels( ) + " channel(s), " + ( int ) format.getSampleRate( ) + " Hz" );
		}
	}
```
Remove the now unused `ArrayWriter` import if nothing else uses it.

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest=OperationsRecognitionTraceTest`; Expected: PASS (6 tests).

- [ ] **Step 5: Checkpoint**

Run: `mvn -q test`; Expected: all pass (including `OperationsRecognitionTest` and `OperationsBaseDirTest`).

---

### Task 6: Training traces in `Operations`

**Files:**
- Create: `src/org/ioe/tprsa/trace/CodebookTrace.java`, `src/org/ioe/tprsa/trace/WordTrainingTrace.java`, `src/org/ioe/tprsa/trace/TrainingSession.java`
- Modify: `src/org/ioe/tprsa/classify/speech/vq/Codebook.java`, `src/org/ioe/tprsa/classify/speech/HiddenMarkov.java`, `src/org/ioe/tprsa/mediator/Operations.java`
- Test: `test/org/ioe/tprsa/mediator/OperationsTrainingTraceTest.java`, extend `HiddenMarkovTest.java`

**Interfaces:**
- Produces: `record CodebookTrace( int trainingVectors, List<SplitTrace> splits, int[] vectorsPerCodeword )` with nested `record SplitTrace( int codebookSize, double[] distortions )` (distortion right after the split, then after each k-means iteration) and `unusedCodewords()`; `Codebook.getTrace()`; `HiddenMarkov.train()` now returns `double[]` (log-likelihood of the training sequences under the model before each re-estimation); `HiddenMarkov.MAX_ITERATIONS` public; `HiddenMarkov.getTransition()/getOutput()` (deep copies); `record WordTrainingTrace( String word, List<String> files, List<int[]> codewordSequences, double[] logLikelihoods, boolean converged, double[][] initialTransition, double[][] initialOutput, double[][] finalTransition, double[][] finalOutput, String skippedReason )` with `skipped(word, reason)`, `isSkipped()`, `iterations()`, `finalLogLikelihood()`; `record TrainingSession( CodebookTrace codebook, List<WordTrainingTrace> words )` with `EMPTY`, `withCodebook`, `withWords`, `word( String )`, `wordNames()`; `Operations.generateCodebookWithTrace( Consumer<String> progress ) : CodebookTrace`, `Operations.hmmTrainWithTrace( Consumer<String> progress ) : List<WordTrainingTrace>`.
- Note on the spec: the LBG distortion is a sum of (non-squared) distances while k-means minimises squared distances, so it is not strictly monotonic per iteration; the tests pin "last ≤ first within a split" and "more codewords → lower final distortion" instead.

- [ ] **Step 1: Write the failing tests**

Add to `HiddenMarkovTest.java`:
```java
	@Test
	void trainReturnsTheLogLikelihoodPerIteration( ) {
		int[][] seqs = utterances( new int[] { 0, 1, 2, 3 }, 8, 8, 31 );
		HiddenMarkov hmm = new HiddenMarkov( 4, 8, new Random( 2 ) );
		hmm.setTrainSeq( seqs );
		double[] ll = hmm.train( );
		assertTrue( ll.length >= 2 && ll.length <= HiddenMarkov.MAX_ITERATIONS );
		assertEquals( totalLogLikelihood( new HiddenMarkov( 4, 8, new Random( 2 ) ), seqs ), ll[ 0 ], 1e-9, "first value: the initial model" );
		for ( int i = 1; i < ll.length; i++ ) {
			assertTrue( ll[ i ] >= ll[ i - 1 ] - 1e-3, "iteration " + i + ": " + ll[ i - 1 ] + " -> " + ll[ i ] );
		}
		double[][] a = hmm.getTransition( );
		a[ 0 ][ 0 ] = 42;
		assertNotEquals( 42, hmm.getTransition( )[ 0 ][ 0 ], "returns a copy" );
	}
```
(`totalLogLikelihood` calls `setObSeq` through `getProbability`; it is already defined in the test class.)

`test/org/ioe/tprsa/mediator/OperationsTrainingTraceTest.java`:
```java
package org.ioe.tprsa.mediator;

import org.ioe.tprsa.TestFiles;
import org.ioe.tprsa.classify.speech.HiddenMarkov;
import org.ioe.tprsa.db.ObjectIODataBase;
import org.ioe.tprsa.trace.CodebookTrace;
import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.trace.WordTrainingTrace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag( "integration" )
class OperationsTrainingTraceTest {

	@TempDir
	static Path				dir;
	static CodebookTrace	codebook;
	static List< WordTrainingTrace > words;
	static final List< String > progress = new ArrayList<>( );

	@BeforeAll
	static void train( ) throws Exception {
		TestFiles.copyTree( Paths.get( "TrainWav" ), dir.resolve( "TrainWav" ) );
		Files.createDirectories( dir.resolve( "TrainWav/Empty" ) ); // a word folder without recordings
		Operations op = new Operations( dir );
		codebook = op.generateCodebookWithTrace( progress::add );
		words = op.hmmTrainWithTrace( progress::add );
	}

	@Test
	void modelsAreIdenticalToTheProjectModels( ) throws Exception {
		OperationsBaseDirTest.assertModelsIdentical( dir );
		assertFalse( Files.exists( dir.resolve( "models/HMM/Empty.hmm" ) ), "skipped words get no model" );
	}

	@Test
	void codebookTraceShowsTheLbgSplits( ) {
		assertEquals( List.of( 2, 4, 8, 16, 32, 64, 128, 256 ), codebook.splits( ).stream( ).map( CodebookTrace.SplitTrace::codebookSize ).toList( ) );
		assertEquals( 256, codebook.vectorsPerCodeword( ).length );
		assertEquals( codebook.trainingVectors( ), Arrays.stream( codebook.vectorsPerCodeword( ) ).sum( ) );
		double previousFinal = Double.POSITIVE_INFINITY;
		for ( CodebookTrace.SplitTrace s : codebook.splits( ) ) {
			double[] d = s.distortions( );
			assertTrue( d.length >= 2, "distortion after the split plus at least one k-means iteration" );
			assertTrue( d[ d.length - 1 ] <= d[ 0 ], s.codebookSize( ) + " codewords: k-means must not end worse than it started" );
			assertTrue( d[ d.length - 1 ] < previousFinal, "more codewords, lower distortion" );
			previousFinal = d[ d.length - 1 ];
		}
	}

	@Test
	void wordTracesDescribeEachTraining( ) throws Exception {
		assertEquals( List.of( "Apple", "Developer", "Empty", "Hello", "Ship", "Zebra" ), words.stream( ).map( WordTrainingTrace::word ).toList( ) );
		WordTrainingTrace empty = words.get( 2 );
		assertTrue( empty.isSkipped( ) );
		assertEquals( "no .wav files", empty.skippedReason( ) );

		for ( WordTrainingTrace w : words ) {
			if ( w.isSkipped( ) ) {
				continue;
			}
			assertEquals( dir.resolve( "TrainWav" ).resolve( w.word( ) ).toFile( ).list( ).length, w.files( ).size( ) );
			assertEquals( w.files( ).size( ), w.codewordSequences( ).size( ) );
			for ( int[] seq : w.codewordSequences( ) ) {
				assertTrue( seq.length > 0 );
				assertTrue( Arrays.stream( seq ).allMatch( c -> c >= 0 && c < 256 ) );
			}
			assertEquals( w.iterations( ) < HiddenMarkov.MAX_ITERATIONS, w.converged( ) );
			assertEquals( 6, w.initialTransition( ).length );
			assertEquals( 256, w.finalOutput( )[ 0 ].length );
			HiddenMarkov saved = new HiddenMarkov( w.word( ), new ObjectIODataBase( dir ) );
			assertArrayEquals( saved.getTransition( ), w.finalTransition( ) );
			assertArrayEquals( saved.getOutput( ), w.finalOutput( ) );
		}
	}

	@Test
	void progressIsReported( ) {
		assertTrue( progress.contains( "Training word 3/6: Empty" ), progress.toString( ) );
		assertTrue( progress.stream( ).anyMatch( p -> p.startsWith( "Generating codebook" ) ), progress.toString( ) );
	}

	@Test
	void trainingSessionCombinesBothSteps( ) {
		TrainingSession s = TrainingSession.EMPTY.withCodebook( codebook ).withWords( words );
		assertSame( codebook, s.codebook( ) );
		assertEquals( 6, s.wordNames( ).size( ) );
		assertTrue( s.word( "hello" ).isPresent( ) );
		assertNull( TrainingSession.EMPTY.codebook( ) );
	}
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest='HiddenMarkovTest,OperationsTrainingTraceTest'`; Expected: compilation FAILURE, `cannot find symbol class CodebookTrace`.

- [ ] **Step 3: Implement**

`src/org/ioe/tprsa/trace/CodebookTrace.java`:
```java
package org.ioe.tprsa.trace;

import java.util.Arrays;
import java.util.List;

/**
 * how the LBG algorithm built the codebook
 *
 * @param splits
 *            one entry per codebook size reached by splitting (2, 4 … 256)
 * @param vectorsPerCodeword
 *            number of training vectors quantised to each codeword
 */
public record CodebookTrace( int trainingVectors, List< SplitTrace > splits, int[] vectorsPerCodeword ) {

	public CodebookTrace {
		splits = List.copyOf( splits );
	}

	/**
	 * @param distortions
	 *            total distortion right after the split, then after each k-means iteration
	 */
	public record SplitTrace( int codebookSize, double[] distortions ) {
	}

	public int unusedCodewords( ) {
		return ( int ) Arrays.stream( vectorsPerCodeword ).filter( n -> n == 0 ).count( );
	}
}
```

`src/org/ioe/tprsa/trace/WordTrainingTrace.java`:
```java
package org.ioe.tprsa.trace;

import java.util.List;

/**
 * how one word's HMM was trained
 *
 * @param logLikelihoods
 *            total log likelihood of the training sequences under the model before each Baum-Welch re-estimation
 * @param converged
 *            stopped by the convergence threshold rather than the iteration limit
 * @param skippedReason
 *            why the word was not trained, or null
 */
public record WordTrainingTrace( String word, List< String > files, List< int[] > codewordSequences, double[] logLikelihoods,
		boolean converged, double[][] initialTransition, double[][] initialOutput, double[][] finalTransition, double[][] finalOutput,
		String skippedReason ) {

	public WordTrainingTrace {
		files = List.copyOf( files );
		codewordSequences = List.copyOf( codewordSequences );
	}

	public static WordTrainingTrace skipped( String word, String reason ) {
		return new WordTrainingTrace( word, List.of( ), List.of( ), new double[ 0 ], false, null, null, null, null, reason );
	}

	public boolean isSkipped( ) {
		return skippedReason != null;
	}

	public int iterations( ) {
		return logLikelihoods.length;
	}

	public double finalLogLikelihood( ) {
		return logLikelihoods.length == 0 ? Double.NaN : logLikelihoods[ logLikelihoods.length - 1 ];
	}
}
```

`src/org/ioe/tprsa/trace/TrainingSession.java`:
```java
package org.ioe.tprsa.trace;

import java.util.List;
import java.util.Optional;

/**
 * the latest codebook generation and HMM training of this session; either may be missing
 */
public record TrainingSession( CodebookTrace codebook, List< WordTrainingTrace > words ) {

	public static final TrainingSession EMPTY = new TrainingSession( null, List.of( ) );

	public TrainingSession {
		words = List.copyOf( words );
	}

	public TrainingSession withCodebook( CodebookTrace newCodebook ) {
		return new TrainingSession( newCodebook, words );
	}

	public TrainingSession withWords( List< WordTrainingTrace > newWords ) {
		return new TrainingSession( codebook, newWords );
	}

	public Optional< WordTrainingTrace > word( String name ) {
		return words.stream( ).filter( w -> w.word( ).equalsIgnoreCase( name ) ).findFirst( );
	}

	public List< String > wordNames( ) {
		return words.stream( ).map( WordTrainingTrace::word ).toList( );
	}
}
```

`Codebook.java`: add imports `java.util.ArrayList`, `java.util.List`, `org.ioe.tprsa.trace.CodebookTrace`. Add the field after `protected int dimension;`:
```java
	/**
	 * distortions recorded by {@link #initialize()}, one entry per split
	 */
	private final List<CodebookTrace.SplitTrace> splitTraces = new ArrayList<>();
```
In `initialize()`, after the `groupPtoC();` that follows `split();`, add `List<Double> distortions = new ArrayList<>();` and `distortions.add(totalDistortion());`. Inside the k-means loop, directly after the loop that sums `distortion_after_update`, add `distortions.add(distortion_after_update);`. After the k-means `for` loop (still inside the `while`), add:
```java
			splitTraces.add(new CodebookTrace.SplitTrace(centroids.length, distortions.stream().mapToDouble(Double::doubleValue).toArray()));
```
Add the methods:
```java
	private double totalDistortion() {
		double sum = 0;
		for (Centroid centroid : centroids) {
			sum += centroid.getDistortion();
		}
		return sum;
	}

	/**
	 * how this codebook was trained; only for a codebook trained from points, not one loaded from a file
	 */
	public CodebookTrace getTrace() {
		if (pt == null) {
			throw new IllegalStateException("only a codebook trained in this session has a trace");
		}
		int[] counts = new int[centroids.length];
		for (int c : quantize(pt)) {
			counts[c]++;
		}
		return new CodebookTrace(pt.length, splitTraces, counts);
	}
```

`HiddenMarkov.java`: change `static final int MAX_ITERATIONS` to `public static final int MAX_ITERATIONS`. Replace `train()`:
```java
	/**
	 * train the hmm model until no more improvement<br>
	 * calls: none<br>
	 * called by: trainHMM
	 *
	 * @return total log likelihood of the training sequences under the model before each re-estimation
	 */
	public double[] train( ) {
		List< Double > logLikelihoods = new ArrayList<>( );
		double previous = Double.NEGATIVE_INFINITY;
		for ( int i = 0; i < MAX_ITERATIONS; i++ ) {
			double logLikelihood = reestimate( );
			logLikelihoods.add( logLikelihood );
			if ( Math.abs( logLikelihood - previous ) < CONVERGENCE_THRESHOLD * Math.abs( logLikelihood ) ) {
				break;
			}
			previous = logLikelihood;
		}
		return logLikelihoods.stream( ).mapToDouble( Double::doubleValue ).toArray( );
	}
```
(add imports `java.util.ArrayList`, `java.util.List`) and add:
```java
	/** @return copy of the transition matrix a[i][j] */
	public double[][] getTransition( ) {
		return deepCopy( transition );
	}

	/** @return copy of the output matrix b[state][symbol] */
	public double[][] getOutput( ) {
		return deepCopy( output );
	}

	private static double[][] deepCopy( double[][] m ) {
		double[][] c = new double[ m.length ][ ];
		for ( int i = 0; i < m.length; i++ ) {
			c[ i ] = m[ i ].clone( );
		}
		return c;
	}
```

`Operations.java`: add imports `java.util.Arrays`, `java.util.Collections`, `java.util.function.Consumer`, `org.ioe.tprsa.trace.CodebookTrace`, `org.ioe.tprsa.trace.WordTrainingTrace`. Replace `generateCodebook()` and `hmmTrain()` (and delete the `allFeaturesList` field) with:
```java
	public void generateCodebook( ) throws Exception {
		generateCodebookWithTrace( message -> { } );
	}

	public void hmmTrain( ) throws Exception {
		hmmTrainWithTrace( message -> { } );
	}

	/**
	 * trains the VQ codebook on the feature vectors of all training recordings and saves it
	 *
	 * @param progress
	 *            receives short status messages
	 */
	public CodebookTrace generateCodebookWithTrace( Consumer< String > progress ) throws Exception {
		progress.accept( "Extracting features from the training recordings" );
		trainTestWavs = new TrainingTestingWaveFiles( "train", baseDir );
		List< double[] > allFeatures = new ArrayList<>( );
		for ( File[] wordFiles : trainTestWavs.readWaveFilesList( ) ) {
			for ( File wav : wordFiles ) {
				Collections.addAll( allFeatures, extractFeatureFromFile( wav ).getFeatureVector( ) );
			}
		}
		if ( allFeatures.size( ) < CODEBOOK_SIZE ) {
			throw new IllegalStateException( "Not enough training data: " + allFeatures.size( ) + " feature vectors, at least " + CODEBOOK_SIZE + " needed" );
		}
		Points[] pts = new Points[ allFeatures.size( ) ];
		for ( int j = 0; j < pts.length; j++ ) {
			pts[ j ] = new Points( allFeatures.get( j ) );
		}
		progress.accept( "Generating codebook: " + CODEBOOK_SIZE + " codewords from " + pts.length + " feature vectors" );
		Codebook cbk = new Codebook( pts, CODEBOOK_SIZE );
		CodebookTrace trace = cbk.getTrace( );
		cbk.saveToFile( new ObjectIODataBase( baseDir ) );
		progress.accept( "Codebook saved" );
		return trace;
	}

	/**
	 * trains and saves one HMM per word folder in TrainWav/
	 *
	 * @param progress
	 *            receives short status messages
	 */
	public List< WordTrainingTrace > hmmTrainWithTrace( Consumer< String > progress ) throws Exception {
		File codebookFile = baseDir.resolve( "models" ).resolve( "codeBook" ).resolve( "codebook.cbk" ).toFile( );
		if ( !codebookFile.isFile( ) ) {
			throw new IllegalStateException( "Train first: no codebook found (" + codebookFile + "), run Generate CodeBook" );
		}
		trainTestWavs = new TrainingTestingWaveFiles( "train", baseDir );
		cb = new Codebook( new ObjectIODataBase( baseDir ) );
		wavFiles = trainTestWavs.readWaveFilesList( );
		words = trainTestWavs.readWordWavFolder( );
		List< WordTrainingTrace > traces = new ArrayList<>( );
		for ( int i = 0; i < wavFiles.length; i++ ) {
			String currentWord = words.get( i );
			progress.accept( "Training word " + ( i + 1 ) + "/" + wavFiles.length + ": " + currentWord );
			if ( wavFiles[ i ].length == 0 ) {
				traces.add( WordTrainingTrace.skipped( currentWord, "no .wav files" ) );
				continue;
			}
			List< String > names = new ArrayList<>( );
			int[][] quantized = new int[ wavFiles[ i ].length ][];
			for ( int j = 0; j < wavFiles[ i ].length; j++ ) {
				names.add( wavFiles[ i ][ j ].getName( ) );
				quantized[ j ] = cb.quantize( getPointsFromFeatureVector( extractFeatureFromFile( wavFiles[ i ][ j ] ) ) );
			}
			// fixed seed: retraining on the same recordings gives the same models
			mkv = new HiddenMarkov( NUM_STATES, CODEBOOK_SIZE, new Random( currentWord.hashCode( ) ) );
			double[][] initialTransition = mkv.getTransition( );
			double[][] initialOutput = mkv.getOutput( );
			mkv.setTrainSeq( quantized );
			double[] logLikelihoods = mkv.train( );
			mkv.save( currentWord, new ObjectIODataBase( baseDir ) );
			traces.add( new WordTrainingTrace( currentWord, names, Arrays.asList( quantized ), logLikelihoods,
					logLikelihoods.length < HiddenMarkov.MAX_ITERATIONS, initialTransition, initialOutput, mkv.getTransition( ),
					mkv.getOutput( ), null ) );
		}
		progress.accept( "HMM training completed" );
		return traces;
	}
```

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest='HiddenMarkovTest,OperationsTrainingTraceTest,OperationsBaseDirTest'`; Expected: PASS.

- [ ] **Step 5: Checkpoint**

Run: `mvn -q test`; Expected: all pass.

---

### Task 7: Chart toolkit and step-view contract

**Files:**
- Modify: `pom.xml` (dependency)
- Create: `src/org/ioe/tprsa/ui/viz/ViewState.java`, `StepView.java`, `Charts.java`
- Create: `test/org/ioe/tprsa/ui/viz/ViewTestSupport.java`, `test/org/ioe/tprsa/ui/viz/ChartsTest.java`

**Interfaces:**
- Produces: `record ViewState( int frame, String word )` with `frameIn( int count )` (clamped); `interface StepView<T> { String title(); String explanation( T trace, ViewState state ); JComponent build( T trace, ViewState state ); default boolean usesFrame(); default boolean usesWord(); }`; `Charts` static API: `record Series( String name, double[] x, double[] y )` + `Series.of( name, double[] y )`; `line( title, xLabel, yLabel, Series... )`; `stepLine( title, xLabel, yLabel, Series... )`; `bar( title, categoryLabel, valueLabel, String[] labels, double[] values, int highlight, int mark )`; `xyBars( title, xLabel, yLabel, double[] values )`; `heatMap( title, xLabel, yLabel, double[][] z )` (z[x][y], NaN = empty cell, −∞ = lowest colour); `transpose( double[][] )`; `envelope( name, float[] y, double xStep, int maxPoints ) : Series`; `toDouble( float[] )`; `indices( int n )`; `addFrameCursor( chart, double x )`; `addInterval( chart, double from, double to, Color )`; `addSecondary( chart, String axisLabel, Series... )`; `annotateCells( chart, double[][] z, String format )`; `panel( chart ) : ChartPanel`; `stack( JComponent... )`; `message( String ) : JComponent`; `table( String[] columns, Object[][] rows ) : JComponent`; colours `NOISE`, `VOICED`, `SELECTED`, `HIGHLIGHT`, `MARK`, `BAR`. Test support: `ViewTestSupport.renderAll( JComponent ) : int` (renders every chart inside, returns how many).

- [ ] **Step 1: Write the failing test**

`test/org/ioe/tprsa/ui/viz/ViewTestSupport.java`:
```java
package org.ioe.tprsa.ui.viz;

import org.jfree.chart.ChartPanel;

import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

final class ViewTestSupport {

	private ViewTestSupport( ) {
	}

	/** renders every chart inside the component to an image; returns the number of charts */
	static int renderAll( Component c ) {
		int count = 0;
		if ( c instanceof ChartPanel panel ) {
			BufferedImage img = panel.getChart( ).createBufferedImage( 640, 400 );
			assertEquals( 640, img.getWidth( ) );
			count++;
		}
		if ( c instanceof Container container ) {
			for ( Component child : container.getComponents( ) ) {
				count += renderAll( child );
			}
		}
		return count;
	}
}
```

`test/org/ioe/tprsa/ui/viz/ChartsTest.java`:
```java
package org.ioe.tprsa.ui.viz;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.XYPlot;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

class ChartsTest {

	@Test
	void lineAndStepCharts( ) {
		JFreeChart c = Charts.line( "t", "x", "y", Charts.Series.of( "a", new double[] { 1, 3, 2 } ), Charts.Series.of( "b", new double[] { 0, 1, 0 } ) );
		assertEquals( 2, c.getXYPlot( ).getDataset( ).getSeriesCount( ) );
		Charts.addFrameCursor( c, 1 );
		Charts.addInterval( c, 0, 1, Charts.VOICED );
		Charts.addSecondary( c, "weight", Charts.Series.of( "w", new double[] { 0, 1, 0 } ) );
		assertEquals( 2, c.getXYPlot( ).getDatasetCount( ) );
		assertEquals( 1, ViewTestSupport.renderAll( Charts.panel( c ) ) );
		assertEquals( 1, ViewTestSupport.renderAll( Charts.panel( Charts.stepLine( "s", "x", "y", Charts.Series.of( "q", new double[] { 0, 0, 1 } ) ) ) ) );
	}

	@Test
	void barHighlightsAndMarks( ) {
		JFreeChart c = Charts.bar( "b", "word", "score", new String[] { "a", "b", "c" }, new double[] { -1, -2, -3 }, 0, 2 );
		CategoryPlot plot = c.getCategoryPlot( );
		assertEquals( 3, plot.getDataset( ).getColumnCount( ) );
		assertEquals( Charts.HIGHLIGHT, plot.getRenderer( ).getItemPaint( 0, 0 ) );
		assertEquals( Charts.MARK, plot.getRenderer( ).getItemPaint( 0, 2 ) );
		assertEquals( Charts.BAR, plot.getRenderer( ).getItemPaint( 0, 1 ) );
		Charts.panel( c ).getChart( ).createBufferedImage( 300, 200 );
	}

	@Test
	void heatMapSkipsNaNAndClampsMinusInfinity( ) {
		double[][] z = { { 1, Double.NaN }, { Double.NEGATIVE_INFINITY, 4 } };
		JFreeChart c = Charts.heatMap( "h", "x", "y", z );
		XYPlot plot = c.getXYPlot( );
		assertEquals( 3, plot.getDataset( ).getItemCount( 0 ), "the NaN cell is left empty" );
		Charts.annotateCells( c, z, "%.1f" );
		c.createBufferedImage( 300, 200 );
		Charts.heatMap( "constant", "x", "y", new double[][] { { 2, 2 } } ).createBufferedImage( 300, 200 );
	}

	@Test
	void helpers( ) {
		assertArrayEquals( new double[][] { { 1, 3 }, { 2, 4 } }, Charts.transpose( new double[][] { { 1, 2 }, { 3, 4 } } ) );
		float[] y = new float[ 10000 ];
		y[ 5000 ] = 7;
		Charts.Series s = Charts.envelope( "e", y, 0.5, 100 );
		assertTrue( s.y( ).length <= 200 );
		assertEquals( 7, java.util.Arrays.stream( s.y( ) ).max( ).orElseThrow( ), "the peak survives down-sampling" );
		assertEquals( 2.0, Charts.envelope( "e", new float[] { 1, 2, 3 }, 1, 100 ).x( )[ 2 ] );
		assertEquals( 2, new ViewState( 7, null ).frameIn( 3 ) );
		assertEquals( 0, new ViewState( -1, null ).frameIn( 3 ) );
		JComponent table = Charts.table( new String[] { "a" }, new Object[][] { { 1 } } );
		assertNotNull( table );
		assertNotNull( Charts.message( "hello" ) );
		assertEquals( 2, ViewTestSupport.renderAll( Charts.stack( Charts.panel( Charts.line( "a", "x", "y", Charts.Series.of( "a", new double[] { 1 } ) ) ) ,
				Charts.panel( Charts.xyBars( "b", "x", "y", new double[] { 1, 2 } ) ) ) ) );
		assertNotEquals( Color.WHITE, Charts.NOISE );
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=ChartsTest`; Expected: compilation FAILURE, `package org.jfree.chart does not exist`.

- [ ] **Step 3: Implement**

`pom.xml`: inside `<dependencies>` before junit:
```xml
        <dependency>
            <groupId>org.jfree</groupId>
            <artifactId>jfreechart</artifactId>
            <version>1.5.6</version>
        </dependency>
```

`src/org/ioe/tprsa/ui/viz/ViewState.java`:
```java
package org.ioe.tprsa.ui.viz;

/**
 * what the user selected in the inspector
 *
 * @param frame
 *            selected frame (0-based) for time based steps
 * @param word
 *            selected word for word based steps, or null
 */
public record ViewState( int frame, String word ) {

	/** the selected frame clamped to 0..count-1 */
	public int frameIn( int count ) {
		return Math.max( 0, Math.min( count - 1, frame ) );
	}
}
```

`src/org/ioe/tprsa/ui/viz/StepView.java`:
```java
package org.ioe.tprsa.ui.viz;

import javax.swing.JComponent;

/**
 * one step of the algorithm, rendered from a trace record; never calls the algorithm itself
 */
public interface StepView< T > {

	String title( );

	/** what the step does, with this run's key numbers */
	String explanation( T trace, ViewState state );

	JComponent build( T trace, ViewState state );

	/** whether the frame slider affects this step */
	default boolean usesFrame( ) {
		return false;
	}

	/** whether the word picker affects this step */
	default boolean usesWord( ) {
		return false;
	}
}
```

`src/org/ioe/tprsa/ui/viz/Charts.java`:
```java
package org.ioe.tprsa.ui.viz;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardXYZToolTipGenerator;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYStepRenderer;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.DefaultXYZDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * small factory for the charts of the step views (JFreeChart); every chart panel has tooltips, zoom and "Save as PNG"
 */
public final class Charts {

	public static final Color	NOISE		= new Color( 120, 120, 120, 60 );
	public static final Color	VOICED		= new Color( 40, 170, 80, 50 );
	public static final Color	SELECTED	= new Color( 230, 140, 20, 70 );
	public static final Color	HIGHLIGHT	= new Color( 30, 110, 200 );
	public static final Color	MARK		= new Color( 230, 120, 20 );
	public static final Color	BAR			= new Color( 150, 160, 175 );

	private Charts( ) {
	}

	/** an x/y data series */
	public record Series( String name, double[] x, double[] y ) {

		/** y against its index 0..n-1 */
		public static Series of( String name, double[] y ) {
			return new Series( name, indices( y.length ), y );
		}
	}

	public static double[] indices( int n ) {
		double[] x = new double[ n ];
		for ( int i = 0; i < n; i++ ) {
			x[ i ] = i;
		}
		return x;
	}

	public static double[] toDouble( float[] values ) {
		double[] d = new double[ values.length ];
		for ( int i = 0; i < values.length; i++ ) {
			d[ i ] = values[ i ];
		}
		return d;
	}

	public static double[][] transpose( double[][] m ) {
		double[][] t = new double[ m[ 0 ].length ][ m.length ];
		for ( int i = 0; i < m.length; i++ ) {
			for ( int j = 0; j < m[ 0 ].length; j++ ) {
				t[ j ][ i ] = m[ i ][ j ];
			}
		}
		return t;
	}

	/**
	 * down-samples a long signal to at most 2 * maxPoints points, keeping each bucket's minimum and maximum
	 *
	 * @param xStep
	 *            x distance between samples (e.g. 1 / sample rate)
	 */
	public static Series envelope( String name, float[] y, double xStep, int maxPoints ) {
		if ( y.length <= 2 * maxPoints ) {
			double[] x = indices( y.length );
			for ( int i = 0; i < x.length; i++ ) {
				x[ i ] *= xStep;
			}
			return new Series( name, x, toDouble( y ) );
		}
		int bucket = ( int ) Math.ceil( y.length / ( double ) maxPoints );
		List< double[] > points = new ArrayList<>( );
		for ( int start = 0; start < y.length; start += bucket ) {
			int end = Math.min( y.length, start + bucket );
			int lo = start, hi = start;
			for ( int i = start; i < end; i++ ) {
				lo = y[ i ] < y[ lo ] ? i : lo;
				hi = y[ i ] > y[ hi ] ? i : hi;
			}
			points.add( new double[] { Math.min( lo, hi ) * xStep, y[ Math.min( lo, hi ) ] } );
			points.add( new double[] { Math.max( lo, hi ) * xStep, y[ Math.max( lo, hi ) ] } );
		}
		return new Series( name, points.stream( ).mapToDouble( p -> p[ 0 ] ).toArray( ), points.stream( ).mapToDouble( p -> p[ 1 ] ).toArray( ) );
	}

	private static XYSeriesCollection collection( Series... series ) {
		XYSeriesCollection c = new XYSeriesCollection( );
		for ( Series s : series ) {
			XYSeries xy = new XYSeries( s.name( ), false, true );
			for ( int i = 0; i < s.y( ).length; i++ ) {
				xy.add( s.x( )[ i ], s.y( )[ i ] );
			}
			c.addSeries( xy );
		}
		return c;
	}

	public static JFreeChart line( String title, String xLabel, String yLabel, Series... series ) {
		JFreeChart chart = ChartFactory.createXYLineChart( title, xLabel, yLabel, collection( series ), PlotOrientation.VERTICAL, series.length > 1, true, false );
		( ( NumberAxis ) chart.getXYPlot( ).getRangeAxis( ) ).setAutoRangeIncludesZero( false );
		return chart;
	}

	public static JFreeChart stepLine( String title, String xLabel, String yLabel, Series... series ) {
		JFreeChart chart = line( title, xLabel, yLabel, series );
		XYStepRenderer renderer = new XYStepRenderer( );
		renderer.setDefaultToolTipGenerator( new org.jfree.chart.labels.StandardXYToolTipGenerator( ) );
		chart.getXYPlot( ).setRenderer( renderer );
		return chart;
	}

	/**
	 * @param highlight
	 *            index of the bar drawn in {@link #HIGHLIGHT}, or -1
	 * @param mark
	 *            index of the bar drawn in {@link #MARK}, or -1
	 */
	public static JFreeChart bar( String title, String categoryLabel, String valueLabel, String[] labels, double[] values, int highlight, int mark ) {
		DefaultCategoryDataset data = new DefaultCategoryDataset( );
		for ( int i = 0; i < labels.length; i++ ) {
			data.addValue( values[ i ], valueLabel, labels[ i ] );
		}
		JFreeChart chart = ChartFactory.createBarChart( title, categoryLabel, valueLabel, data, PlotOrientation.VERTICAL, false, true, false );
		BarRenderer renderer = new BarRenderer( ) {
			@Override
			public Paint getItemPaint( int row, int column ) {
				return column == highlight ? HIGHLIGHT : column == mark ? MARK : BAR;
			}
		};
		renderer.setDefaultToolTipGenerator( new org.jfree.chart.labels.StandardCategoryToolTipGenerator( ) );
		chart.getCategoryPlot( ).setRenderer( renderer );
		return chart;
	}

	/** bars at x = 0..n-1, e.g. a histogram over codewords */
	public static JFreeChart xyBars( String title, String xLabel, String yLabel, double[] values ) {
		return ChartFactory.createXYBarChart( title, xLabel, false, yLabel, collection( Series.of( yLabel, values ) ), PlotOrientation.VERTICAL, false, true, false );
	}

	/**
	 * @param z
	 *            z[x][y]; NaN cells are left empty, -infinity is drawn in the lowest colour
	 */
	public static JFreeChart heatMap( String title, String xLabel, String yLabel, double[][] z ) {
		double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
		for ( double[] column : z ) {
			for ( double v : column ) {
				if ( Double.isFinite( v ) ) {
					min = Math.min( min, v );
					max = Math.max( max, v );
				}
			}
		}
		if ( min == Double.POSITIVE_INFINITY ) {
			min = 0;
			max = 1;
		}
		if ( max <= min ) {
			max = min + 1;
		}
		List< double[] > cells = new ArrayList<>( );
		for ( int x = 0; x < z.length; x++ ) {
			for ( int y = 0; y < z[ x ].length; y++ ) {
				double v = z[ x ][ y ];
				if ( !Double.isNaN( v ) ) {
					cells.add( new double[] { x, y, v == Double.NEGATIVE_INFINITY ? min : v } );
				}
			}
		}
		DefaultXYZDataset data = new DefaultXYZDataset( );
		data.addSeries( title, new double[][] { cells.stream( ).mapToDouble( c -> c[ 0 ] ).toArray( ), cells.stream( ).mapToDouble( c -> c[ 1 ] ).toArray( ),
				cells.stream( ).mapToDouble( c -> c[ 2 ] ).toArray( ) } );
		LookupPaintScale scale = new LookupPaintScale( min, max, Color.WHITE );
		for ( int i = 0; i < 64; i++ ) {
			float f = i / 63f;
			scale.add( min + ( max - min ) * f, new Color( Color.HSBtoRGB( 0.66f - 0.5f * f, 0.85f, 0.35f + 0.6f * f ) ) );
		}
		XYBlockRenderer renderer = new XYBlockRenderer( );
		renderer.setPaintScale( scale );
		renderer.setDefaultToolTipGenerator( new StandardXYZToolTipGenerator( ) );
		NumberAxis xAxis = new NumberAxis( xLabel );
		NumberAxis yAxis = new NumberAxis( yLabel );
		xAxis.setAutoRangeIncludesZero( false );
		yAxis.setAutoRangeIncludesZero( false );
		XYPlot plot = new XYPlot( data, xAxis, yAxis, renderer );
		JFreeChart chart = new JFreeChart( title, JFreeChart.DEFAULT_TITLE_FONT, plot, false );
		PaintScaleLegend legend = new PaintScaleLegend( scale, new NumberAxis( ) );
		legend.setPosition( RectangleEdge.RIGHT );
		chart.addSubtitle( legend );
		return chart;
	}

	/** writes each cell's value into a heat map, e.g. for small matrices */
	public static void annotateCells( JFreeChart chart, double[][] z, String format ) {
		for ( int x = 0; x < z.length; x++ ) {
			for ( int y = 0; y < z[ x ].length; y++ ) {
				if ( Double.isFinite( z[ x ][ y ] ) ) {
					chart.getXYPlot( ).addAnnotation( new XYTextAnnotation( String.format( format, z[ x ][ y ] ), x, y ) );
				}
			}
		}
	}

	/** vertical line at x, e.g. the selected frame */
	public static void addFrameCursor( JFreeChart chart, double x ) {
		ValueMarker marker = new ValueMarker( x, MARK, new BasicStroke( 1.5f ) );
		chart.getXYPlot( ).addDomainMarker( marker );
	}

	/** shaded x interval */
	public static void addInterval( JFreeChart chart, double from, double to, Color color ) {
		chart.getXYPlot( ).addDomainMarker( new IntervalMarker( from, to, color ), org.jfree.chart.ui.Layer.BACKGROUND );
	}

	/** extra series on a second y axis (not listed in the legend) */
	public static void addSecondary( JFreeChart chart, String axisLabel, Series... series ) {
		XYPlot plot = chart.getXYPlot( );
		int index = plot.getDatasetCount( );
		plot.setDataset( index, collection( series ) );
		plot.setRangeAxis( index, new NumberAxis( axisLabel ) );
		plot.mapDatasetToRangeAxis( index, index );
		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer( true, false );
		renderer.setDefaultSeriesVisibleInLegend( false );
		renderer.setAutoPopulateSeriesPaint( false );
		renderer.setDefaultPaint( new Color( 200, 60, 60, 140 ) );
		plot.setRenderer( index, renderer );
	}

	public static ChartPanel panel( JFreeChart chart ) {
		ChartPanel panel = new ChartPanel( chart );
		panel.setMouseWheelEnabled( true );
		panel.setPreferredSize( new Dimension( 600, 260 ) );
		return panel;
	}

	/** components on top of each other, sharing the height */
	public static JComponent stack( JComponent... parts ) {
		JPanel p = new JPanel( new GridLayout( parts.length, 1, 0, 4 ) );
		for ( JComponent part : parts ) {
			p.add( part );
		}
		return p;
	}

	/** a centred text, e.g. when a step has nothing to show yet */
	public static JComponent message( String text ) {
		JLabel label = new JLabel( "<html><div style='text-align:center'>" + text + "</div></html>", SwingConstants.CENTER );
		label.setForeground( Color.DARK_GRAY );
		return label;
	}

	public static JComponent table( String[] columns, Object[][] rows ) {
		JTable table = new JTable( new DefaultTableModel( rows, columns ) {
			@Override
			public boolean isCellEditable( int row, int column ) {
				return false;
			}
		} );
		table.setAutoCreateRowSorter( true );
		return new JScrollPane( table );
	}
}
```

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest=ChartsTest`; Expected: PASS (4 tests).

- [ ] **Step 5: Checkpoint**

Run: `mvn -q test`; Expected: all pass.

---

### Task 8: Recognition views 1–4 (waveform, framing, spectrum, MFCC)

**Files:**
- Create: `src/org/ioe/tprsa/ui/viz/recognition/WaveformStep.java`, `FramingStep.java`, `SpectrumStep.java`, `MfccStep.java`
- Create: `test/org/ioe/tprsa/ui/viz/TraceFixtures.java`, `test/org/ioe/tprsa/ui/viz/RecognitionViewsTest.java`

**Interfaces:**
- Consumes: `RecognitionTrace`, `PreprocessTrace`, `FeatureTrace`, `Charts`, `StepView`, `ViewState`.
- Produces: four `StepView<RecognitionTrace>` classes with no-arg constructors; `WaveformStep.runs( boolean[] flags ) : List<int[]>` ([start, endExclusive] of each run of true); test fixture `TraceFixtures.misrecognized()` (Ship3.wav verified as "Ship", heard as another word with the committed models), `TraceFixtures.silent()` (0.5 s of zeros), `TraceFixtures.training()` (Task 10).

- [ ] **Step 1: Write the failing test**

`test/org/ioe/tprsa/ui/viz/TraceFixtures.java`:
```java
package org.ioe.tprsa.ui.viz;

import org.ioe.tprsa.TestFiles;
import org.ioe.tprsa.mediator.Operations;
import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.trace.TrainingSession;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** real traces computed once per test run, shared by the view tests */
final class TraceFixtures {

	private static RecognitionTrace	misrecognized;
	private static RecognitionTrace	silent;
	private static TrainingSession	training;

	private TraceFixtures( ) {
	}

	/** verification of Ship3.wav as "Ship", which the committed models recognise as another word */
	static synchronized RecognitionTrace misrecognized( ) throws Exception {
		if ( misrecognized == null ) {
			misrecognized = new Operations( ).recognizeWithTrace( new File( "TrainWav/Ship/Ship3.wav" ), "Ship" );
		}
		return misrecognized;
	}

	/** 0.5 s of digital silence */
	static synchronized RecognitionTrace silent( ) throws Exception {
		if ( silent == null ) {
			silent = new Operations( ).recognizeWithTrace( new float[ 11025 ], null );
		}
		return silent;
	}

	/** codebook + HMM training on a temporary copy of TrainWav plus an empty word folder */
	static synchronized TrainingSession training( ) throws Exception {
		if ( training == null ) {
			Path dir = Files.createTempDirectory( "viz-training" );
			TestFiles.copyTree( Paths.get( "TrainWav" ), dir.resolve( "TrainWav" ) );
			Files.createDirectories( dir.resolve( "TrainWav/Empty" ) );
			Operations op = new Operations( dir );
			training = TrainingSession.EMPTY.withCodebook( op.generateCodebookWithTrace( m -> { } ) ).withWords( op.hmmTrainWithTrace( m -> { } ) );
		}
		return training;
	}
}
```

`test/org/ioe/tprsa/ui/viz/RecognitionViewsTest.java`:
```java
package org.ioe.tprsa.ui.viz;

import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.ui.viz.recognition.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag( "integration" )
class RecognitionViewsTest {

	static List< StepView< RecognitionTrace > > firstFour( ) {
		return List.of( new WaveformStep( ), new FramingStep( ), new SpectrumStep( ), new MfccStep( ) );
	}

	@Test
	void everyViewRendersForAMisrecognition( ) throws Exception {
		RecognitionTrace t = TraceFixtures.misrecognized( );
		int middle = t.features( ).frameCount( ) / 2;
		for ( StepView< RecognitionTrace > v : firstFour( ) ) {
			for ( ViewState s : List.of( new ViewState( 0, null ), new ViewState( middle, "Ship" ), new ViewState( 10_000, null ) ) ) {
				assertTrue( ViewTestSupport.renderAll( v.build( t, s ) ) >= 1, v.title( ) );
				assertFalse( v.explanation( t, s ).isBlank( ), v.title( ) );
			}
		}
	}

	@Test
	void explanationsCarryTheKeyNumbers( ) throws Exception {
		RecognitionTrace t = TraceFixtures.misrecognized( );
		assertTrue( new WaveformStep( ).explanation( t, new ViewState( 0, null ) ).contains( "σ" ) );
		assertTrue( new FramingStep( ).explanation( t, new ViewState( 4, null ) ).contains( "Frame 5/" ) );
		assertTrue( new SpectrumStep( ).explanation( t, new ViewState( 0, null ) ).contains( "30 triangular filters" ) );
		assertTrue( new MfccStep( ).explanation( t, new ViewState( 0, null ) ).contains( "√(2/30)" ) );
	}

	@Test
	void runsOfVoicedFrames( ) {
		List< int[] > runs = WaveformStep.runs( new boolean[] { false, true, true, false, true } );
		assertEquals( 2, runs.size( ) );
		assertArrayEquals( new int[] { 1, 3 }, runs.get( 0 ) );
		assertArrayEquals( new int[] { 4, 5 }, runs.get( 1 ) );
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=RecognitionViewsTest`; Expected: compilation FAILURE, `package org.ioe.tprsa.ui.viz.recognition does not exist`.

- [ ] **Step 3: Implement**

`src/org/ioe/tprsa/ui/viz/recognition/WaveformStep.java`:
```java
package org.ioe.tprsa.ui.viz.recognition;

import org.ioe.tprsa.trace.PreprocessTrace;
import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.List;

/** step 1: normalised waveform with the noise window and the frames kept by end point detection */
public final class WaveformStep implements StepView< RecognitionTrace > {

	@Override
	public String title( ) {
		return "1. Waveform & end point detection";
	}

	@Override
	public String explanation( RecognitionTrace trace, ViewState state ) {
		PreprocessTrace p = trace.preprocess( );
		String text = String.format( "The recording (%.2f s) is normalised to a peak of 1. The first 200 ms (grey) estimate the background noise: "
				+ "mean μ = %.4f, σ = %.4f. A sample is voiced when |x − μ| / σ ≥ %.0f; 10 ms frames with mostly voiced samples (green) are kept: "
				+ "%.0f %% of the signal.", p.durationSec( ), p.noiseMean( ), p.noiseSd( ), p.voicedThreshold( ), 100 * p.keptFraction( ) );
		return p.wholeSignalUsed( ) ? text + " No speech detected: the whole signal is used." : text;
	}

	@Override
	public JComponent build( RecognitionTrace trace, ViewState state ) {
		PreprocessTrace p = trace.preprocess( );
		double rate = p.sampleRate( );
		JFreeChart chart = Charts.line( "Normalised waveform", "time (s)", "amplitude", Charts.envelope( "signal", p.normalisedSignal( ), 1 / rate, 4000 ) );
		Charts.addInterval( chart, 0, p.noiseSamples( ) / rate, Charts.NOISE );
		for ( int[] run : runs( p.voicedFrames( ) ) ) {
			Charts.addInterval( chart, run[ 0 ] * p.epdFrameSize( ) / rate, run[ 1 ] * p.epdFrameSize( ) / rate, Charts.VOICED );
		}
		return Charts.panel( chart );
	}

	/** [start, endExclusive] of every run of true values */
	public static List< int[] > runs( boolean[] flags ) {
		List< int[] > runs = new ArrayList<>( );
		int start = -1;
		for ( int i = 0; i <= flags.length; i++ ) {
			boolean on = i < flags.length && flags[ i ];
			if ( on && start < 0 ) {
				start = i;
			} else if ( !on && start >= 0 ) {
				runs.add( new int[] { start, i } );
				start = -1;
			}
		}
		return runs;
	}
}
```

`src/org/ioe/tprsa/ui/viz/recognition/FramingStep.java`:
```java
package org.ioe.tprsa.ui.viz.recognition;

import org.ioe.tprsa.trace.PreprocessTrace;
import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;

/** step 2: frames of the trimmed signal, pre-emphasis and the Hamming window for the selected frame */
public final class FramingStep implements StepView< RecognitionTrace > {

	@Override
	public String title( ) {
		return "2. Framing & windowing";
	}

	@Override
	public boolean usesFrame( ) {
		return true;
	}

	@Override
	public String explanation( RecognitionTrace trace, ViewState state ) {
		PreprocessTrace p = trace.preprocess( );
		int f = state.frameIn( p.frameCount( ) );
		return String.format( "The trimmed signal is cut into frames of %d samples (%.1f ms), a new frame every %d samples (50 %% overlap): %d frames. "
				+ "Frame %d/%d: pre-emphasis s'(n) = s(n) − %.2f·s(n−1) boosts the high frequencies, then the Hamming window tapers the frame edges.",
				p.frameSize( ), 1000.0 * p.frameSize( ) / p.sampleRate( ), p.hop( ), p.frameCount( ), f + 1, p.frameCount( ), p.preEmphasis( ) );
	}

	@Override
	public JComponent build( RecognitionTrace trace, ViewState state ) {
		PreprocessTrace p = trace.preprocess( );
		int f = state.frameIn( p.frameCount( ) );
		double rate = p.sampleRate( );
		JFreeChart signal = Charts.line( "Trimmed signal", "time (s)", "amplitude", Charts.envelope( "trimmed", p.trimmedSignal( ), 1 / rate, 4000 ) );
		Charts.addInterval( signal, f * p.hop( ) / rate, ( f * p.hop( ) + p.frameSize( ) ) / rate, Charts.SELECTED );
		JFreeChart frame = Charts.line( "Frame " + ( f + 1 ), "sample", "amplitude", Charts.Series.of( "raw", Charts.toDouble( p.rawFrames( )[ f ] ) ),
				Charts.Series.of( "pre-emphasised", Charts.toDouble( p.preEmphasisedFrames( )[ f ] ) ),
				Charts.Series.of( "windowed", Charts.toDouble( p.windowedFrames( )[ f ] ) ) );
		JFreeChart window = Charts.line( "Hamming window", "sample", "weight", Charts.Series.of( "w(n)", Charts.toDouble( p.hammingWindow( ) ) ) );
		return Charts.stack( Charts.panel( signal ), Charts.panel( frame ), Charts.panel( window ) );
	}
}
```

`src/org/ioe/tprsa/ui/viz/recognition/SpectrumStep.java`:
```java
package org.ioe.tprsa.ui.viz.recognition;

import org.ioe.tprsa.trace.FeatureTrace;
import org.ioe.tprsa.trace.PreprocessTrace;
import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;

/** step 3: magnitude spectrum of the selected frame with the mel filter bank, and its log filter bank energies */
public final class SpectrumStep implements StepView< RecognitionTrace > {

	@Override
	public String title( ) {
		return "3. Spectrum & mel filter bank";
	}

	@Override
	public boolean usesFrame( ) {
		return true;
	}

	@Override
	public String explanation( RecognitionTrace trace, ViewState state ) {
		PreprocessTrace p = trace.preprocess( );
		int f = state.frameIn( trace.features( ).frameCount( ) );
		return String.format( "Frame %d: the %d-point FFT gives the magnitude spectrum |X(k)| up to %d Hz. 30 triangular filters, equally spaced on the "
				+ "mel scale mel(f) = 2595·log10(1 + f/700) between 80 Hz and %d Hz (red, right axis), sum the spectrum into 30 band energies, "
				+ "which are then log compressed (bottom).", f + 1, p.frameSize( ), p.sampleRate( ) / 2, p.sampleRate( ) / 2 );
	}

	@Override
	public JComponent build( RecognitionTrace trace, ViewState state ) {
		FeatureTrace ft = trace.features( );
		PreprocessTrace p = trace.preprocess( );
		int f = state.frameIn( ft.frameCount( ) );
		double hzPerBin = p.sampleRate( ) / ( double ) p.frameSize( );
		double[] magnitude = ft.magnitudeSpectra( )[ f ];
		double[] hz = new double[ magnitude.length ], db = new double[ magnitude.length ];
		for ( int k = 0; k < magnitude.length; k++ ) {
			hz[ k ] = k * hzPerBin;
			db[ k ] = 20 * Math.log10( Math.max( magnitude[ k ], 1e-12 ) );
		}
		JFreeChart spectrum = Charts.line( "Magnitude spectrum, frame " + ( f + 1 ), "frequency (Hz)", "|X(k)| (dB)", new Charts.Series( "spectrum", hz, db ) );
		int[] bins = ft.melCentreBins( );
		Charts.Series[] filters = new Charts.Series[ bins.length - 2 ];
		for ( int k = 1; k + 1 < bins.length; k++ ) {
			filters[ k - 1 ] = new Charts.Series( "filter " + k, new double[] { bins[ k - 1 ] * hzPerBin, bins[ k ] * hzPerBin, bins[ k + 1 ] * hzPerBin },
					new double[] { 0, 1, 0 } );
		}
		Charts.addSecondary( spectrum, "filter weight", filters );
		String[] labels = new String[ ft.logMelEnergies( )[ f ].length ];
		for ( int i = 0; i < labels.length; i++ ) {
			labels[ i ] = String.valueOf( i + 1 );
		}
		JFreeChart energies = Charts.bar( "Log mel filter bank energies, frame " + ( f + 1 ), "filter", "log energy", labels, ft.logMelEnergies( )[ f ], -1, -1 );
		return Charts.stack( Charts.panel( spectrum ), Charts.panel( energies ) );
	}
}
```

`src/org/ioe/tprsa/ui/viz/recognition/MfccStep.java`:
```java
package org.ioe.tprsa.ui.viz.recognition;

import org.ioe.tprsa.trace.FeatureTrace;
import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;

/** step 4: log mel energies and mean-normalised MFCCs over time */
public final class MfccStep implements StepView< RecognitionTrace > {

	@Override
	public String title( ) {
		return "4. MFCC";
	}

	@Override
	public boolean usesFrame( ) {
		return true;
	}

	@Override
	public String explanation( RecognitionTrace trace, ViewState state ) {
		return String.format( "Each frame's 30 log filter bank energies (top) are decorrelated by a DCT, c_n = √(2/30)·Σ y_i·cos(π·n·(i − 0.5)/30), keeping "
				+ "12 coefficients (c0 included). Subtracting each coefficient's mean over the recording (cepstral mean normalisation) removes a "
				+ "constant microphone / channel effect (bottom). %d frames.", trace.features( ).frameCount( ) );
	}

	@Override
	public JComponent build( RecognitionTrace trace, ViewState state ) {
		FeatureTrace ft = trace.features( );
		int f = state.frameIn( ft.frameCount( ) );
		JFreeChart mel = Charts.heatMap( "Log mel filter bank energies", "frame", "filter", ft.logMelEnergies( ) );
		JFreeChart mfcc = Charts.heatMap( "MFCC after cepstral mean normalisation", "frame", "coefficient", ft.mfcc( ) );
		Charts.addFrameCursor( mel, f );
		Charts.addFrameCursor( mfcc, f );
		return Charts.stack( Charts.panel( mel ), Charts.panel( mfcc ) );
	}
}
```

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest=RecognitionViewsTest`; Expected: PASS (3 tests).

- [ ] **Step 5: Checkpoint**

Run: `mvn -q test`; Expected: all pass.

---

### Task 9: Recognition views 5–8 (deltas & energy, VQ, word scores, best path)

**Files:**
- Create: `src/org/ioe/tprsa/ui/viz/recognition/DeltaEnergyStep.java`, `VqStep.java`, `ScoresStep.java`, `BestPathStep.java`
- Modify: `test/org/ioe/tprsa/ui/viz/RecognitionViewsTest.java`

**Interfaces:**
- Consumes: `RecognitionTrace.scoreOf/rankOf/margin/verified`, `VqTrace`, `WordScore`, `Charts`.
- Produces: four more `StepView<RecognitionTrace>` classes; `RecognitionViewsTest.all()` returns all 8 views in order (used by Task 11).

- [ ] **Step 1: Write the failing tests**

In `RecognitionViewsTest.java`, add the import `org.jfree.chart.ChartPanel` and these members:
```java
	static List< StepView< RecognitionTrace > > all( ) {
		return List.of( new WaveformStep( ), new FramingStep( ), new SpectrumStep( ), new MfccStep( ), new DeltaEnergyStep( ), new VqStep( ),
				new ScoresStep( ), new BestPathStep( ) );
	}

	@Test
	void allEightViewsRender( ) throws Exception {
		RecognitionTrace t = TraceFixtures.misrecognized( );
		for ( StepView< RecognitionTrace > v : all( ) ) {
			for ( String word : List.of( "Ship", "Apple", "nonexistent" ) ) {
				ViewState s = new ViewState( 3, word );
				assertTrue( ViewTestSupport.renderAll( v.build( t, s ) ) >= 1, v.title( ) );
				assertFalse( v.explanation( t, s ).isBlank( ) );
			}
		}
	}

	@Test
	void viewsRenderASilentRecording( ) throws Exception {
		RecognitionTrace t = TraceFixtures.silent( );
		for ( StepView< RecognitionTrace > v : all( ) ) {
			assertTrue( ViewTestSupport.renderAll( v.build( t, new ViewState( 0, null ) ) ) >= 1, v.title( ) );
		}
		assertTrue( new WaveformStep( ).explanation( t, new ViewState( 0, null ) ).contains( "No speech detected" ) );
	}

	@Test
	void scoresAreRankedWithTheRecognisedWordHighlighted( ) throws Exception {
		RecognitionTrace t = TraceFixtures.misrecognized( );
		ChartPanel panel = ( ChartPanel ) new ScoresStep( ).build( t, new ViewState( 0, null ) );
		org.jfree.chart.plot.CategoryPlot plot = panel.getChart( ).getCategoryPlot( );
		assertEquals( t.scores( ).size( ), plot.getDataset( ).getColumnCount( ) );
		assertEquals( t.recognizedWord( ), plot.getDataset( ).getColumnKey( 0 ) );
		assertEquals( Charts.HIGHLIGHT, plot.getRenderer( ).getItemPaint( 0, 0 ) );
		int expected = t.rankOf( "Ship" ) - 1;
		assertEquals( "Ship (expected)", plot.getDataset( ).getColumnKey( expected ) );
		assertEquals( Charts.MARK, plot.getRenderer( ).getItemPaint( 0, expected ) );
		String text = new ScoresStep( ).explanation( t, new ViewState( 0, null ) );
		assertTrue( text.contains( "margin" ) && text.contains( "✘" ), text );
	}

	@Test
	void bestPathComparesTheExpectedAndRecognisedWords( ) throws Exception {
		RecognitionTrace t = TraceFixtures.misrecognized( );
		java.awt.Container stack = ( java.awt.Container ) new BestPathStep( ).build( t, new ViewState( 0, t.recognizedWord( ) ) );
		ChartPanel path = ( ChartPanel ) stack.getComponent( 0 );
		assertEquals( t.verified( ) ? 1 : 2, path.getChart( ).getXYPlot( ).getDataset( ).getSeriesCount( ) );
	}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=RecognitionViewsTest`; Expected: compilation FAILURE, `cannot find symbol class DeltaEnergyStep`.

- [ ] **Step 3: Implement**

`src/org/ioe/tprsa/ui/viz/recognition/DeltaEnergyStep.java`:
```java
package org.ioe.tprsa.ui.viz.recognition;

import org.ioe.tprsa.trace.FeatureTrace;
import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;

/** step 5: delta and delta-delta MFCCs, log energy and its deltas */
public final class DeltaEnergyStep implements StepView< RecognitionTrace > {

	@Override
	public String title( ) {
		return "5. Deltas & energy";
	}

	@Override
	public boolean usesFrame( ) {
		return true;
	}

	@Override
	public String explanation( RecognitionTrace trace, ViewState state ) {
		return "Deltas describe how the coefficients change over time: d_t = Σ_{m=1..M} m·(c_{t+m} − c_{t−m}) / (2·Σ m²), with M = 2 for ΔMFCC and "
				+ "M = 1 for ΔΔMFCC (the delta of the delta); at the start and end the first / last frame is repeated. The log energy log Σ s² of "
				+ "each raw frame and its Δ and ΔΔ complete the 39 values per frame: 12 MFCC + 12 Δ + 12 ΔΔ + 3 energy.";
	}

	@Override
	public JComponent build( RecognitionTrace trace, ViewState state ) {
		FeatureTrace ft = trace.features( );
		int f = state.frameIn( ft.frameCount( ) );
		JFreeChart delta = Charts.heatMap( "ΔMFCC", "frame", "coefficient", ft.deltaMfcc( ) );
		JFreeChart deltaDelta = Charts.heatMap( "ΔΔMFCC", "frame", "coefficient", ft.deltaDeltaMfcc( ) );
		JFreeChart energy = Charts.line( "Log energy", "frame", "value", Charts.Series.of( "log E", ft.logEnergy( ) ),
				Charts.Series.of( "Δ log E", ft.deltaLogEnergy( ) ), Charts.Series.of( "ΔΔ log E", ft.deltaDeltaLogEnergy( ) ) );
		for ( JFreeChart c : new JFreeChart[] { delta, deltaDelta, energy } ) {
			Charts.addFrameCursor( c, f );
		}
		return Charts.stack( Charts.panel( delta ), Charts.panel( deltaDelta ), Charts.panel( energy ) );
	}
}
```

`src/org/ioe/tprsa/ui/viz/recognition/VqStep.java`:
```java
package org.ioe.tprsa.ui.viz.recognition;

import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.trace.VqTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;
import java.util.Arrays;

/** step 6: codeword index and quantization distance per frame */
public final class VqStep implements StepView< RecognitionTrace > {

	@Override
	public String title( ) {
		return "6. Vector quantization";
	}

	@Override
	public boolean usesFrame( ) {
		return true;
	}

	@Override
	public String explanation( RecognitionTrace trace, ViewState state ) {
		VqTrace vq = trace.vq( );
		return String.format( "Each 39-value feature vector is replaced by the index of the nearest of the %d codewords (Euclidean distance); the HMMs "
				+ "only see this sequence of %d symbols. %d distinct codewords are used; mean distance to the chosen codeword %.2f.",
				vq.codebookSize( ), vq.codewords( ).length, vq.distinctCodewords( ), vq.meanDistance( ) );
	}

	@Override
	public JComponent build( RecognitionTrace trace, ViewState state ) {
		VqTrace vq = trace.vq( );
		int f = state.frameIn( vq.codewords( ).length );
		double[] codewords = Arrays.stream( vq.codewords( ) ).asDoubleStream( ).toArray( );
		JFreeChart symbols = Charts.stepLine( "Codeword per frame", "frame", "codeword", Charts.Series.of( "codeword", codewords ) );
		JFreeChart distances = Charts.line( "Distance to the chosen codeword", "frame", "distance", Charts.Series.of( "distance", vq.distances( ) ) );
		Charts.addFrameCursor( symbols, f );
		Charts.addFrameCursor( distances, f );
		return Charts.stack( Charts.panel( symbols ), Charts.panel( distances ) );
	}
}
```

`src/org/ioe/tprsa/ui/viz/recognition/ScoresStep.java`:
```java
package org.ioe.tprsa.ui.viz.recognition;

import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.trace.WordScore;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;

import javax.swing.JComponent;
import java.util.List;

/** step 7: every word model's Viterbi score, ranked */
public final class ScoresStep implements StepView< RecognitionTrace > {

	@Override
	public String title( ) {
		return "7. Word scores";
	}

	@Override
	public String explanation( RecognitionTrace trace, ViewState state ) {
		List< WordScore > s = trace.scores( );
		StringBuilder text = new StringBuilder( String.format( "Each word's HMM scores the codeword sequence with the Viterbi algorithm (log probability of "
				+ "the best state path; higher is better). Recognised: %s (%.1f).", s.get( 0 ).word( ), s.get( 0 ).score( ) ) );
		if ( s.size( ) > 1 ) {
			text.append( String.format( " Runner-up: %s (%.1f), margin %.1f.", s.get( 1 ).word( ), s.get( 1 ).score( ), trace.margin( ) ) );
		} else {
			text.append( " Only one word model is trained." );
		}
		if ( trace.isVerification( ) ) {
			text.append( trace.verified( ) ? String.format( " Expected %s: ✔ verified.", trace.expectedWord( ) )
					: trace.scoreOf( trace.expectedWord( ) ).map( e -> String.format( " Expected %s: ✘ not verified, it scored %.1f (rank %d).",
							trace.expectedWord( ), e.score( ), trace.rankOf( trace.expectedWord( ) ) ) )
							.orElse( String.format( " Expected %s: ✘ no model for this word.", trace.expectedWord( ) ) ) );
		}
		return text.toString( );
	}

	@Override
	public JComponent build( RecognitionTrace trace, ViewState state ) {
		List< WordScore > s = trace.scores( );
		String[] labels = new String[ s.size( ) ];
		double[] values = new double[ s.size( ) ];
		int mark = -1;
		for ( int i = 0; i < s.size( ); i++ ) {
			boolean expected = trace.isVerification( ) && s.get( i ).word( ).equalsIgnoreCase( trace.expectedWord( ) );
			labels[ i ] = s.get( i ).word( ) + ( expected ? " (expected)" : "" );
			values[ i ] = s.get( i ).score( );
			mark = expected && i > 0 ? i : mark;
		}
		return Charts.panel( Charts.bar( "Viterbi log score per word", "word", "log score", labels, values, 0, mark ) );
	}
}
```

`src/org/ioe/tprsa/ui/viz/recognition/BestPathStep.java`:
```java
package org.ioe.tprsa.ui.viz.recognition;

import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.trace.WordScore;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;
import java.util.Arrays;
import java.util.Optional;

/** step 8: best state path and Viterbi score grid of the chosen word, compared with the expected / recognised word */
public final class BestPathStep implements StepView< RecognitionTrace > {

	@Override
	public String title( ) {
		return "8. Best path";
	}

	@Override
	public boolean usesFrame( ) {
		return true;
	}

	@Override
	public boolean usesWord( ) {
		return true;
	}

	private static WordScore chosen( RecognitionTrace trace, ViewState state ) {
		return state.word( ) == null ? trace.scores( ).get( 0 ) : trace.scoreOf( state.word( ) ).orElse( trace.scores( ).get( 0 ) );
	}

	/** when verification failed: the other one of {expected, recognised} */
	private static Optional< WordScore > comparison( RecognitionTrace trace, WordScore chosen ) {
		if ( !trace.isVerification( ) || trace.verified( ) ) {
			return Optional.empty( );
		}
		String other = chosen.word( ).equalsIgnoreCase( trace.recognizedWord( ) ) ? trace.expectedWord( ) : trace.recognizedWord( );
		return trace.scoreOf( other ).filter( s -> !s.word( ).equalsIgnoreCase( chosen.word( ) ) );
	}

	@Override
	public String explanation( RecognitionTrace trace, ViewState state ) {
		WordScore w = chosen( trace, state );
		int states = w.viterbiGrid( )[ 0 ].length;
		String text = String.format( "The Viterbi algorithm finds the most likely sequence of the %d states of %s's left-to-right model (each frame a state "
				+ "may stay, move to the next state or skip one). Score %.1f, rank %d of %d.", states, w.word( ), w.score( ), trace.rankOf( w.word( ) ),
				trace.scores( ).size( ) );
		return text + comparison( trace, w ).map( o -> String.format( " Also shown: the %s model's path (score %.1f).", o.word( ), o.score( ) ) ).orElse( "" );
	}

	@Override
	public JComponent build( RecognitionTrace trace, ViewState state ) {
		WordScore w = chosen( trace, state );
		int f = state.frameIn( w.statePath( ).length );
		Optional< WordScore > other = comparison( trace, w );
		Charts.Series mine = Charts.Series.of( w.word( ), Arrays.stream( w.statePath( ) ).asDoubleStream( ).toArray( ) );
		JFreeChart path = other.isPresent( )
				? Charts.stepLine( "Best state path", "frame", "state", mine,
						Charts.Series.of( other.get( ).word( ), Arrays.stream( other.get( ).statePath( ) ).asDoubleStream( ).toArray( ) ) )
				: Charts.stepLine( "Best state path", "frame", "state", mine );
		JFreeChart symbols = Charts.stepLine( "Codeword per frame", "frame", "codeword",
				Charts.Series.of( "codeword", Arrays.stream( trace.vq( ).codewords( ) ).asDoubleStream( ).toArray( ) ) );
		JFreeChart grid = Charts.heatMap( "Viterbi log scores, " + w.word( ), "frame", "state", w.viterbiGrid( ) );
		for ( JFreeChart c : new JFreeChart[] { path, symbols, grid } ) {
			Charts.addFrameCursor( c, f );
		}
		return Charts.stack( Charts.panel( path ), Charts.panel( symbols ), Charts.panel( grid ) );
	}
}
```

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest=RecognitionViewsTest`; Expected: PASS (7 tests).

- [ ] **Step 5: Checkpoint**

Run: `mvn -q test`; Expected: all pass.

---

### Task 10: Training views

**Files:**
- Create: `src/org/ioe/tprsa/ui/viz/training/CodebookStep.java`, `WordSteps.java`, `TrainingSequencesStep.java`, `ConvergenceStep.java`, `ModelStep.java`, `SummaryStep.java`
- Create: `test/org/ioe/tprsa/ui/viz/TrainingViewsTest.java`

**Interfaces:**
- Consumes: `TrainingSession`, `CodebookTrace`, `WordTrainingTrace`, `Charts`, `TraceFixtures.training()`.
- Produces: five `StepView<TrainingSession>` classes; `TrainingViewsTest.all()` (used by Task 11). Word steps take the word from `ViewState.word()` (falls back to the first word); a missing codebook / no words / a skipped word render `Charts.message(…)`.

- [ ] **Step 1: Write the failing test**

`test/org/ioe/tprsa/ui/viz/TrainingViewsTest.java`:
```java
package org.ioe.tprsa.ui.viz;

import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.ui.viz.training.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag( "integration" )
class TrainingViewsTest {

	static List< StepView< TrainingSession > > all( ) {
		return List.of( new CodebookStep( ), new TrainingSequencesStep( ), new ConvergenceStep( ), new ModelStep( ), new SummaryStep( ) );
	}

	@Test
	void everyViewRendersForEveryWord( ) throws Exception {
		TrainingSession s = TraceFixtures.training( );
		for ( StepView< TrainingSession > v : all( ) ) {
			for ( String word : s.wordNames( ) ) {
				JComponent c = v.build( s, new ViewState( 0, word ) );
				assertNotNull( c, v.title( ) );
				ViewTestSupport.renderAll( c );
				assertFalse( v.explanation( s, new ViewState( 0, word ) ).isBlank( ), v.title( ) );
			}
		}
		assertEquals( 2, ViewTestSupport.renderAll( new CodebookStep( ).build( s, new ViewState( 0, null ) ) ) );
		assertEquals( 2, ViewTestSupport.renderAll( new ModelStep( ).build( s, new ViewState( 0, "Hello" ) ) ) );
	}

	@Test
	void skippedWordAndMissingStepsShowAMessage( ) throws Exception {
		TrainingSession s = TraceFixtures.training( );
		assertInstanceOf( JLabel.class, new ConvergenceStep( ).build( s, new ViewState( 0, "Empty" ) ) );
		assertTrue( ( ( JLabel ) new ModelStep( ).build( s, new ViewState( 0, "Empty" ) ) ).getText( ).contains( "no .wav files" ) );
		assertInstanceOf( JLabel.class, new CodebookStep( ).build( TrainingSession.EMPTY.withWords( s.words( ) ), new ViewState( 0, null ) ) );
		assertInstanceOf( JLabel.class, new TrainingSequencesStep( ).build( TrainingSession.EMPTY.withCodebook( s.codebook( ) ), new ViewState( 0, null ) ) );
	}

	@Test
	void summaryHasOneRowPerWord( ) throws Exception {
		TrainingSession s = TraceFixtures.training( );
		JTable table = ( JTable ) ( ( JScrollPane ) new SummaryStep( ).build( s, new ViewState( 0, null ) ) ).getViewport( ).getView( );
		assertEquals( s.words( ).size( ), table.getRowCount( ) );
		assertEquals( "Empty", table.getValueAt( 2, 0 ) );
		assertEquals( "no .wav files", table.getValueAt( 2, 5 ) );
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=TrainingViewsTest`; Expected: compilation FAILURE, `package org.ioe.tprsa.ui.viz.training does not exist`.

- [ ] **Step 3: Implement**

`src/org/ioe/tprsa/ui/viz/training/CodebookStep.java`:
```java
package org.ioe.tprsa.ui.viz.training;

import org.ioe.tprsa.trace.CodebookTrace;
import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;
import java.util.Arrays;
import java.util.List;

/** training step 1: LBG splitting and k-means distortion, codeword usage */
public final class CodebookStep implements StepView< TrainingSession > {

	@Override
	public String title( ) {
		return "1. Codebook (LBG)";
	}

	@Override
	public String explanation( TrainingSession session, ViewState state ) {
		CodebookTrace c = session.codebook( );
		if ( c == null ) {
			return "Run Generate CodeBook to see how the codebook is built.";
		}
		List< CodebookTrace.SplitTrace > splits = c.splits( );
		double[] last = splits.get( splits.size( ) - 1 ).distortions( );
		return String.format( "LBG: starting from the mean of all %d training feature vectors, every codeword c is split into c·(1 + 0.01) and c·(1 − 0.01), "
				+ "then k-means assigns each vector to its nearest codeword and moves each codeword to the mean of its vectors until the distortion "
				+ "(sum of distances) stops improving. Repeated until there are %d codewords; final distortion %.1f, %d codewords unused.",
				c.trainingVectors( ), c.vectorsPerCodeword( ).length, last[ last.length - 1 ], c.unusedCodewords( ) );
	}

	@Override
	public JComponent build( TrainingSession session, ViewState state ) {
		CodebookTrace c = session.codebook( );
		if ( c == null ) {
			return Charts.message( "Run Generate CodeBook to see this step." );
		}
		Charts.Series[] series = c.splits( ).stream( ).map( s -> Charts.Series.of( s.codebookSize( ) + " codewords", s.distortions( ) ) )
				.toArray( Charts.Series[]::new );
		JFreeChart distortion = Charts.line( "k-means distortion after each split", "k-means iteration (0 = right after the split)", "distortion", series );
		JFreeChart usage = Charts.xyBars( "Training vectors per codeword", "codeword", "vectors",
				Arrays.stream( c.vectorsPerCodeword( ) ).asDoubleStream( ).toArray( ) );
		return Charts.stack( Charts.panel( distortion ), Charts.panel( usage ) );
	}
}
```

`src/org/ioe/tprsa/ui/viz/training/WordSteps.java` (shared lookup for the word based steps):
```java
package org.ioe.tprsa.ui.viz.training;

import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.trace.WordTrainingTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.ViewState;

import javax.swing.JComponent;
import java.util.Optional;

/** shared helpers of the word based training steps */
final class WordSteps {

	private WordSteps( ) {
	}

	/** the selected word, or the first one */
	static Optional< WordTrainingTrace > word( TrainingSession session, ViewState state ) {
		if ( session.words( ).isEmpty( ) ) {
			return Optional.empty( );
		}
		return Optional.of( state.word( ) == null ? session.words( ).get( 0 ) : session.word( state.word( ) ).orElse( session.words( ).get( 0 ) ) );
	}

	/** message for a missing or skipped word, or null when the word can be shown */
	static JComponent unavailable( Optional< WordTrainingTrace > word ) {
		if ( word.isEmpty( ) ) {
			return Charts.message( "Run Train HMM to see this step." );
		}
		if ( word.get( ).isSkipped( ) ) {
			return Charts.message( word.get( ).word( ) + " was skipped: " + word.get( ).skippedReason( ) );
		}
		return null;
	}
}
```

`src/org/ioe/tprsa/ui/viz/training/TrainingSequencesStep.java`:
```java
package org.ioe.tprsa.ui.viz.training;

import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.trace.WordTrainingTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;

import javax.swing.JComponent;
import java.util.Arrays;
import java.util.Optional;

/** training step 2: codeword sequence of each recording of the selected word */
public final class TrainingSequencesStep implements StepView< TrainingSession > {

	@Override
	public String title( ) {
		return "2. Training sequences";
	}

	@Override
	public boolean usesWord( ) {
		return true;
	}

	@Override
	public String explanation( TrainingSession session, ViewState state ) {
		return WordSteps.word( session, state ).filter( w -> !w.isSkipped( ) )
				.map( w -> String.format( "Each of the %d recordings of %s is quantised with the codebook into a sequence of codewords (one row per recording: %s). "
						+ "Baum-Welch trains the HMM on these sequences.", w.files( ).size( ), w.word( ), String.join( ", ", w.files( ) ) ) )
				.orElse( "Run Train HMM to see the training sequences." );
	}

	@Override
	public JComponent build( TrainingSession session, ViewState state ) {
		Optional< WordTrainingTrace > word = WordSteps.word( session, state );
		JComponent unavailable = WordSteps.unavailable( word );
		if ( unavailable != null ) {
			return unavailable;
		}
		WordTrainingTrace w = word.get( );
		int longest = w.codewordSequences( ).stream( ).mapToInt( s -> s.length ).max( ).orElse( 0 );
		double[][] z = new double[ longest ][ w.codewordSequences( ).size( ) ];
		for ( double[] column : z ) {
			Arrays.fill( column, Double.NaN );
		}
		for ( int r = 0; r < w.codewordSequences( ).size( ); r++ ) {
			int[] seq = w.codewordSequences( ).get( r );
			for ( int t = 0; t < seq.length; t++ ) {
				z[ t ][ r ] = seq[ t ];
			}
		}
		return Charts.panel( Charts.heatMap( "Codeword sequences of " + w.word( ), "frame", "recording", z ) );
	}
}
```

`src/org/ioe/tprsa/ui/viz/training/ConvergenceStep.java`:
```java
package org.ioe.tprsa.ui.viz.training;

import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.trace.WordTrainingTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;
import java.util.Optional;

/** training step 3: Baum-Welch log likelihood per iteration */
public final class ConvergenceStep implements StepView< TrainingSession > {

	@Override
	public String title( ) {
		return "3. Baum-Welch convergence";
	}

	@Override
	public boolean usesWord( ) {
		return true;
	}

	@Override
	public String explanation( TrainingSession session, ViewState state ) {
		return WordSteps.word( session, state ).filter( w -> !w.isSkipped( ) )
				.map( w -> String.format( "Baum-Welch (EM) re-estimates the transition and output probabilities so that the training sequences become more "
						+ "likely; each iteration can only increase the total log likelihood. %s stopped after %d iterations (%s), log likelihood %.1f.",
						w.word( ), w.iterations( ), w.converged( ) ? "converged: change below 1e-5" : "iteration limit reached", w.finalLogLikelihood( ) ) )
				.orElse( "Run Train HMM to see the convergence." );
	}

	@Override
	public JComponent build( TrainingSession session, ViewState state ) {
		Optional< WordTrainingTrace > word = WordSteps.word( session, state );
		JComponent unavailable = WordSteps.unavailable( word );
		if ( unavailable != null ) {
			return unavailable;
		}
		WordTrainingTrace w = word.get( );
		double[] iteration = Charts.indices( w.iterations( ) );
		for ( int i = 0; i < iteration.length; i++ ) {
			iteration[ i ] += 1;
		}
		JFreeChart chart = Charts.line( "Log likelihood of the training sequences, " + w.word( ), "iteration", "log likelihood",
				new Charts.Series( "log likelihood", iteration, w.logLikelihoods( ) ) );
		Charts.addFrameCursor( chart, w.iterations( ) );
		return Charts.panel( chart );
	}
}
```

`src/org/ioe/tprsa/ui/viz/training/ModelStep.java`:
```java
package org.ioe.tprsa.ui.viz.training;

import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.trace.WordTrainingTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;
import java.util.Optional;

/** training step 4: the learned transition and output matrices */
public final class ModelStep implements StepView< TrainingSession > {

	@Override
	public String title( ) {
		return "4. Learned model";
	}

	@Override
	public boolean usesWord( ) {
		return true;
	}

	@Override
	public String explanation( TrainingSession session, ViewState state ) {
		return WordSteps.word( session, state ).filter( w -> !w.isSkipped( ) )
				.map( w -> String.format( "%s's left-to-right model: a[i][j] (top) is the probability of moving from state i to state j; only j = i, i+1, i+2 "
						+ "are allowed. b[j][k] (bottom) is the probability that state j emits codeword k; every probability is floored at 1e-4.",
						w.word( ) ) )
				.orElse( "Run Train HMM to see the learned models." );
	}

	@Override
	public JComponent build( TrainingSession session, ViewState state ) {
		Optional< WordTrainingTrace > word = WordSteps.word( session, state );
		JComponent unavailable = WordSteps.unavailable( word );
		if ( unavailable != null ) {
			return unavailable;
		}
		WordTrainingTrace w = word.get( );
		double[][] a = Charts.transpose( w.finalTransition( ) ); // x = to state, y = from state
		JFreeChart transition = Charts.heatMap( "Transition probabilities a[from][to], " + w.word( ), "to state", "from state", a );
		Charts.annotateCells( transition, a, "%.2f" );
		JFreeChart output = Charts.heatMap( "Output probabilities b[state][codeword]", "codeword", "state", Charts.transpose( w.finalOutput( ) ) );
		return Charts.stack( Charts.panel( transition ), Charts.panel( output ) );
	}
}
```

`src/org/ioe/tprsa/ui/viz/training/SummaryStep.java`:
```java
package org.ioe.tprsa.ui.viz.training;

import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.trace.WordTrainingTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;

import javax.swing.JComponent;

/** training step 5: one row per word */
public final class SummaryStep implements StepView< TrainingSession > {

	@Override
	public String title( ) {
		return "5. Summary";
	}

	@Override
	public String explanation( TrainingSession session, ViewState state ) {
		long skipped = session.words( ).stream( ).filter( WordTrainingTrace::isSkipped ).count( );
		return session.words( ).isEmpty( ) ? "Run Train HMM to see the summary."
				: String.format( "%d words trained, %d skipped.", session.words( ).size( ) - skipped, skipped );
	}

	@Override
	public JComponent build( TrainingSession session, ViewState state ) {
		if ( session.words( ).isEmpty( ) ) {
			return Charts.message( "Run Train HMM to see this step." );
		}
		Object[][] rows = session.words( ).stream( ).map( w -> new Object[] { w.word( ), w.files( ).size( ), w.iterations( ),
				w.isSkipped( ) ? "" : String.format( "%.1f", w.finalLogLikelihood( ) ), w.isSkipped( ) ? "" : w.converged( ) ? "yes" : "no",
				w.isSkipped( ) ? w.skippedReason( ) : "" } ).toArray( Object[][]::new );
		return Charts.table( new String[] { "word", "recordings", "iterations", "final log likelihood", "converged", "skipped" }, rows );
	}
}
```

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest=TrainingViewsTest`; Expected: PASS (3 tests).

- [ ] **Step 5: Checkpoint**

Run: `mvn -q test`; Expected: all pass.

---

### Task 11: Inspectors

**Files:**
- Create: `src/org/ioe/tprsa/ui/viz/Inspector.java`, `RecognitionInspector.java`, `TrainingInspector.java`
- Create: `test/org/ioe/tprsa/ui/viz/InspectorTest.java`

**Interfaces:**
- Consumes: all step views, `TraceFixtures`.
- Produces: `abstract class Inspector<T> extends JPanel` with `show( T trace )`, test hooks `selectStep( int )`, `selectWord( String )`, `setFrame( int )`, `stepCount()`, `currentView() : JComponent`, `explanationText()`, `headerText()`, `isFrameControlVisible()`, `isWordControlVisible()`; `new RecognitionInspector()`; `new TrainingInspector()`.

- [ ] **Step 1: Write the failing test**

`test/org/ioe/tprsa/ui/viz/InspectorTest.java`:
```java
package org.ioe.tprsa.ui.viz;

import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.trace.TrainingSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;

import static org.junit.jupiter.api.Assertions.*;

@Tag( "integration" )
class InspectorTest {

	@Test
	void recognitionInspectorShowsEveryStep( ) throws Exception {
		RecognitionInspector inspector = new RecognitionInspector( );
		assertInstanceOf( JLabel.class, inspector.currentView( ), "empty state before the first run" );
		RecognitionTrace t = TraceFixtures.misrecognized( );
		inspector.show( t );
		assertEquals( 8, inspector.stepCount( ) );
		assertTrue( inspector.headerText( ).contains( t.recognizedWord( ) ) && inspector.headerText( ).contains( "Ship" ) );
		for ( int i = 0; i < inspector.stepCount( ); i++ ) {
			inspector.selectStep( i );
			assertTrue( ViewTestSupport.renderAll( inspector.currentView( ) ) >= 1, "step " + i );
			assertFalse( inspector.explanationText( ).isBlank( ) );
		}
		inspector.selectStep( 1 );
		assertTrue( inspector.isFrameControlVisible( ) );
		assertFalse( inspector.isWordControlVisible( ) );
		inspector.setFrame( 4 );
		assertTrue( inspector.explanationText( ).contains( "Frame 5/" ), inspector.explanationText( ) );
		inspector.selectStep( 7 );
		assertTrue( inspector.isWordControlVisible( ) );
		inspector.selectWord( "Ship" );
		assertTrue( inspector.explanationText( ).contains( "Ship's left-to-right model" ), inspector.explanationText( ) );
	}

	@Test
	void trainingInspectorShowsEveryStepAndWord( ) throws Exception {
		TrainingInspector inspector = new TrainingInspector( );
		TrainingSession s = TraceFixtures.training( );
		inspector.show( s );
		assertEquals( 5, inspector.stepCount( ) );
		for ( int i = 0; i < inspector.stepCount( ); i++ ) {
			inspector.selectStep( i );
			for ( String word : s.wordNames( ) ) {
				inspector.selectWord( word );
				assertNotNull( inspector.currentView( ) );
				ViewTestSupport.renderAll( inspector.currentView( ) );
			}
		}
		inspector.show( TrainingSession.EMPTY.withCodebook( s.codebook( ) ) );
		inspector.selectStep( 2 );
		assertInstanceOf( JLabel.class, inspector.currentView( ), "no words trained yet" );
	}

	@Test
	void showingANewTraceKeepsTheSelectedStep( ) throws Exception {
		RecognitionInspector inspector = new RecognitionInspector( );
		inspector.show( TraceFixtures.misrecognized( ) );
		inspector.selectStep( 6 );
		inspector.show( TraceFixtures.silent( ) );
		assertTrue( inspector.explanationText( ).startsWith( "Each word's HMM" ), inspector.explanationText( ) );
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=InspectorTest`; Expected: compilation FAILURE, `cannot find symbol class RecognitionInspector`.

- [ ] **Step 3: Implement**

`src/org/ioe/tprsa/ui/viz/Inspector.java`:
```java
package org.ioe.tprsa.ui.viz;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * step list + the selected step's view + explanation, frame slider and word picker; shows the most recent trace
 */
public abstract class Inspector< T > extends JPanel {

	private final List< StepView< T > >	steps;
	private final JList< String >		stepList;
	private final JLabel				header		= new JLabel( " " );
	private final JPanel				viewHolder	= new JPanel( new BorderLayout( ) );
	private final JTextArea				explanation	= new JTextArea( 3, 40 );
	private final JLabel				frameTitle	= new JLabel( "frame" );
	private final JSlider				frameSlider	= new JSlider( 0, 0, 0 );
	private final JLabel				frameLabel	= new JLabel( );
	private final JLabel				wordTitle	= new JLabel( "word" );
	private final JComboBox< String >	wordBox		= new JComboBox<>( );
	private T							trace;
	private JComponent					currentView;
	private boolean						updating;

	protected Inspector( List< StepView< T > > steps, String emptyMessage ) {
		super( new BorderLayout( 6, 6 ) );
		this.steps = List.copyOf( steps );
		stepList = new JList<>( steps.stream( ).map( StepView::title ).toArray( String[]::new ) );
		stepList.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		stepList.setSelectedIndex( 0 );
		stepList.addListSelectionListener( e -> {
			if ( !e.getValueIsAdjusting( ) ) {
				rebuild( );
			}
		} );
		explanation.setLineWrap( true );
		explanation.setWrapStyleWord( true );
		explanation.setEditable( false );
		frameSlider.addChangeListener( e -> {
			frameLabel.setText( ( frameSlider.getValue( ) + 1 ) + " / " + ( frameSlider.getMaximum( ) + 1 ) );
			if ( !frameSlider.getValueIsAdjusting( ) ) {
				rebuild( );
			}
		} );
		wordBox.addActionListener( e -> rebuild( ) );

		JPanel controls = new JPanel( new FlowLayout( FlowLayout.LEFT ) );
		controls.add( frameTitle );
		controls.add( frameSlider );
		controls.add( frameLabel );
		controls.add( wordTitle );
		controls.add( wordBox );
		JPanel south = new JPanel( new BorderLayout( ) );
		south.add( new JScrollPane( explanation ), BorderLayout.CENTER );
		south.add( controls, BorderLayout.SOUTH );
		JPanel right = new JPanel( new BorderLayout( ) );
		right.add( viewHolder, BorderLayout.CENTER );
		right.add( south, BorderLayout.SOUTH );
		header.setBorder( BorderFactory.createEmptyBorder( 4, 6, 0, 6 ) );
		add( header, BorderLayout.NORTH );
		add( new JScrollPane( stepList ), BorderLayout.WEST );
		add( right, BorderLayout.CENTER );

		currentView = Charts.message( emptyMessage );
		viewHolder.add( currentView );
		setControlsVisible( false, false );
	}

	protected abstract List< String > words( T trace );

	/** word preselected in the picker, or null */
	protected abstract String defaultWord( T trace );

	protected abstract int frameCount( T trace );

	protected abstract String header( T trace );

	/** show a new trace, keeping the selected step */
	public void show( T newTrace ) {
		trace = newTrace;
		updating = true;
		try {
			wordBox.removeAllItems( );
			for ( String w : words( newTrace ) ) {
				wordBox.addItem( w );
			}
			String preselected = defaultWord( newTrace );
			if ( preselected != null ) {
				wordBox.setSelectedItem( preselected );
			}
			int frames = Math.max( 1, frameCount( newTrace ) );
			frameSlider.setMaximum( frames - 1 );
			frameSlider.setValue( frames / 2 );
		} finally {
			updating = false;
		}
		header.setText( header( newTrace ) );
		rebuild( );
	}

	private void rebuild( ) {
		if ( updating || trace == null ) {
			return;
		}
		StepView< T > step = steps.get( Math.max( 0, stepList.getSelectedIndex( ) ) );
		ViewState state = new ViewState( frameSlider.getValue( ), ( String ) wordBox.getSelectedItem( ) );
		setControlsVisible( step.usesFrame( ), step.usesWord( ) && wordBox.getItemCount( ) > 0 );
		frameLabel.setText( ( state.frame( ) + 1 ) + " / " + ( frameSlider.getMaximum( ) + 1 ) );
		viewHolder.removeAll( );
		currentView = step.build( trace, state );
		viewHolder.add( currentView );
		explanation.setText( step.explanation( trace, state ) );
		explanation.setCaretPosition( 0 );
		viewHolder.revalidate( );
		viewHolder.repaint( );
	}

	private void setControlsVisible( boolean frame, boolean word ) {
		frameTitle.setVisible( frame );
		frameSlider.setVisible( frame );
		frameLabel.setVisible( frame );
		wordTitle.setVisible( word );
		wordBox.setVisible( word );
	}

	public void selectStep( int index ) {
		stepList.setSelectedIndex( index );
	}

	public void selectWord( String word ) {
		wordBox.setSelectedItem( word );
	}

	public void setFrame( int frame ) {
		frameSlider.setValue( frame );
	}

	public int stepCount( ) {
		return steps.size( );
	}

	public JComponent currentView( ) {
		return currentView;
	}

	public String explanationText( ) {
		return explanation.getText( );
	}

	public String headerText( ) {
		return header.getText( );
	}

	public boolean isFrameControlVisible( ) {
		return frameSlider.isVisible( );
	}

	public boolean isWordControlVisible( ) {
		return wordBox.isVisible( );
	}
}
```

`src/org/ioe/tprsa/ui/viz/RecognitionInspector.java`:
```java
package org.ioe.tprsa.ui.viz;

import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.trace.WordScore;
import org.ioe.tprsa.ui.viz.recognition.*;

import java.util.List;

/** the 8 recognition / verification steps of the most recent recording */
public final class RecognitionInspector extends Inspector< RecognitionTrace > {

	public RecognitionInspector( ) {
		super( List.of( new WaveformStep( ), new FramingStep( ), new SpectrumStep( ), new MfccStep( ), new DeltaEnergyStep( ), new VqStep( ),
				new ScoresStep( ), new BestPathStep( ) ), "Verify or recognize a word to see each step of the algorithm here." );
	}

	@Override
	protected List< String > words( RecognitionTrace trace ) {
		return trace.scores( ).stream( ).map( WordScore::word ).toList( );
	}

	@Override
	protected String defaultWord( RecognitionTrace trace ) {
		return trace.recognizedWord( );
	}

	@Override
	protected int frameCount( RecognitionTrace trace ) {
		return trace.features( ).frameCount( );
	}

	@Override
	protected String header( RecognitionTrace trace ) {
		String verification = !trace.isVerification( ) ? ""
				: trace.verified( ) ? " &nbsp;(expected " + trace.expectedWord( ) + " ✔)" : " &nbsp;(expected " + trace.expectedWord( ) + " ✘)";
		return "<html><b>" + trace.recognizedWord( ) + "</b> recognized from " + trace.source( ) + verification + "</html>";
	}
}
```

`src/org/ioe/tprsa/ui/viz/TrainingInspector.java`:
```java
package org.ioe.tprsa.ui.viz;

import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.ui.viz.training.*;

import java.util.List;

/** the codebook and HMM training steps of this session */
public final class TrainingInspector extends Inspector< TrainingSession > {

	public TrainingInspector( ) {
		super( List.of( new CodebookStep( ), new TrainingSequencesStep( ), new ConvergenceStep( ), new ModelStep( ), new SummaryStep( ) ),
				"Run Generate CodeBook and Train HMM to see each training step here." );
	}

	@Override
	protected List< String > words( TrainingSession session ) {
		return session.wordNames( );
	}

	@Override
	protected String defaultWord( TrainingSession session ) {
		return session.wordNames( ).isEmpty( ) ? null : session.wordNames( ).get( 0 );
	}

	@Override
	protected int frameCount( TrainingSession session ) {
		return 0;
	}

	@Override
	protected String header( TrainingSession session ) {
		String codebook = session.codebook( ) == null ? "no codebook generated in this session"
				: "codebook of " + session.codebook( ).vectorsPerCodeword( ).length + " codewords";
		return "<html><b>Training</b>: " + codebook + ", " + session.words( ).size( ) + " word(s)</html>";
	}
}
```

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest=InspectorTest`; Expected: PASS (3 tests).

- [ ] **Step 5: Checkpoint**

Run: `mvn -q test`; Expected: all pass.

---

### Task 12: Background jobs, main window, runnable jar, README

**Files:**
- Create: `src/org/ioe/tprsa/ui/JobRunner.java`, `test/org/ioe/tprsa/ui/JobRunnerTest.java`
- Modify: `src/org/ioe/tprsa/ui/HMM_VQ_Speech_Recognition.java`, `pom.xml`, `README.md`

**Interfaces:**
- Consumes: `Operations.recognizeWithTrace/generateCodebookWithTrace/hmmTrainWithTrace`, `RecognitionInspector`, `TrainingInspector`, `TrainingSession`.
- Produces: `JobRunner( JLabel status, JProgressBar bar )`, `register( JComponent... )`, `isRunning()`, `<T> boolean run( String description, Job<T> job, Consumer<T> onSuccess, Consumer<Throwable> onFailure )`, `interface Job<T> { T run( Consumer<String> progress ) throws Exception; }`, `static String message( Throwable )`.

- [ ] **Step 1: Write the failing test**

`test/org/ioe/tprsa/ui/JobRunnerTest.java`:
```java
package org.ioe.tprsa.ui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JobRunnerTest {

	private static boolean onEdt( java.util.function.BooleanSupplier check ) throws Exception {
		boolean[] result = new boolean[ 1 ];
		SwingUtilities.invokeAndWait( ( ) -> result[ 0 ] = check.getAsBoolean( ) );
		return result[ 0 ];
	}

	@Test
	void disablesJobControlsWhileRunningAndDeliversTheResult( ) throws Exception {
		JButton button = new JButton( );
		JLabel status = new JLabel( );
		JobRunner jobs = new JobRunner( status, new JProgressBar( ) );
		jobs.register( button );
		CountDownLatch release = new CountDownLatch( 1 ), finished = new CountDownLatch( 1 );
		AtomicReference< String > result = new AtomicReference<>( );

		SwingUtilities.invokeAndWait( ( ) -> assertTrue( jobs.run( "Job", progress -> {
			progress.accept( "halfway" );
			release.await( 5, TimeUnit.SECONDS );
			return "done!";
		}, r -> {
			result.set( r );
			finished.countDown( );
		}, t -> fail( t ) ) ) );

		assertTrue( jobs.isRunning( ) );
		assertFalse( onEdt( button::isEnabled ) );
		release.countDown( );
		assertTrue( finished.await( 5, TimeUnit.SECONDS ) );
		assertEquals( "done!", result.get( ) );
		assertTrue( onEdt( button::isEnabled ) );
		assertFalse( jobs.isRunning( ) );
	}

	@Test
	void secondJobIsIgnoredWhileOneRuns( ) throws Exception {
		JobRunner jobs = new JobRunner( new JLabel( ), new JProgressBar( ) );
		CountDownLatch release = new CountDownLatch( 1 ), finished = new CountDownLatch( 1 );
		boolean[] started = new boolean[ 2 ];
		SwingUtilities.invokeAndWait( ( ) -> {
			started[ 0 ] = jobs.run( "first", p -> release.await( 5, TimeUnit.SECONDS ), r -> finished.countDown( ), t -> { } );
			started[ 1 ] = jobs.run( "second", p -> "x", r -> fail( "must not run" ), t -> { } );
		} );
		assertTrue( started[ 0 ] );
		assertFalse( started[ 1 ] );
		release.countDown( );
		assertTrue( finished.await( 5, TimeUnit.SECONDS ) );
	}

	@Test
	void failuresAreReportedWithTheirMessage( ) throws Exception {
		JLabel status = new JLabel( );
		JobRunner jobs = new JobRunner( status, new JProgressBar( ) );
		CountDownLatch failed = new CountDownLatch( 1 );
		AtomicReference< Throwable > error = new AtomicReference<>( );
		SwingUtilities.invokeAndWait( ( ) -> jobs.run( "Recognizing", p -> {
			throw new IllegalStateException( "Train first: no codebook found" );
		}, r -> fail( "must fail" ), t -> {
			error.set( t );
			failed.countDown( );
		} ) );
		assertTrue( failed.await( 5, TimeUnit.SECONDS ) );
		assertInstanceOf( IllegalStateException.class, error.get( ) );
		assertTrue( onEdt( ( ) -> status.getText( ).contains( "Train first: no codebook found" ) ) );
		assertEquals( "NullPointerException", JobRunner.message( new NullPointerException( ) ) );
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=JobRunnerTest`; Expected: compilation FAILURE, `cannot find symbol class JobRunner`.

- [ ] **Step 3: Implement `JobRunner`**

`src/org/ioe/tprsa/ui/JobRunner.java`:
```java
package org.ioe.tprsa.ui;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * runs one job at a time in a SwingWorker; job controls are disabled while it runs, progress and errors go to the status line
 */
public final class JobRunner {

	/** work done off the event dispatch thread */
	public interface Job< T > {
		T run( Consumer< String > progress ) throws Exception;
	}

	private final List< JComponent >	controls	= new ArrayList<>( );
	private final JLabel				status;
	private final JProgressBar			bar;
	private volatile boolean			running;

	public JobRunner( JLabel status, JProgressBar bar ) {
		this.status = status;
		this.bar = bar;
		bar.setVisible( false );
	}

	/** controls that start jobs; disabled while any job runs */
	public void register( JComponent... jobControls ) {
		controls.addAll( Arrays.asList( jobControls ) );
	}

	public boolean isRunning( ) {
		return running;
	}

	/**
	 * starts the job unless another one is running; call on the event dispatch thread
	 *
	 * @return false when another job is still running (nothing is started)
	 */
	public < T > boolean run( String description, Job< T > job, Consumer< T > onSuccess, Consumer< Throwable > onFailure ) {
		if ( running ) {
			return false;
		}
		running = true;
		setControlsEnabled( false );
		status.setText( description + " …" );
		bar.setIndeterminate( true );
		bar.setVisible( true );
		new SwingWorker< T, String >( ) {
			@Override
			protected T doInBackground( ) throws Exception {
				return job.run( this::progress );
			}

			private void progress( String message ) {
				publish( message );
			}

			@Override
			protected void process( List< String > messages ) {
				status.setText( messages.get( messages.size( ) - 1 ) );
			}

			@Override
			protected void done( ) {
				running = false;
				setControlsEnabled( true );
				bar.setVisible( false );
				try {
					T result = get( );
					status.setText( description + ": done" );
					onSuccess.accept( result );
				} catch ( ExecutionException e ) {
					Throwable cause = e.getCause( ) == null ? e : e.getCause( );
					status.setText( description + " failed: " + message( cause ) );
					onFailure.accept( cause );
				} catch ( InterruptedException | CancellationException e ) {
					status.setText( description + " cancelled" );
				}
			}
		}.execute( );
		return true;
	}

	private void setControlsEnabled( boolean enabled ) {
		for ( JComponent c : controls ) {
			c.setEnabled( enabled );
		}
	}

	/** readable message of an exception */
	public static String message( Throwable t ) {
		return t.getMessage( ) == null ? t.getClass( ).getSimpleName( ) : t.getMessage( );
	}
}
```

- [ ] **Step 4: Run the JobRunner tests**

Run: `mvn -q test -Dtest=JobRunnerTest`; Expected: PASS (3 tests).

- [ ] **Step 5: Wire the main window**

In `HMM_VQ_Speech_Recognition.java` add imports `org.ioe.tprsa.trace.RecognitionTrace`, `org.ioe.tprsa.trace.TrainingSession`, `org.ioe.tprsa.ui.viz.RecognitionInspector`, `org.ioe.tprsa.ui.viz.TrainingInspector`, and these fields after `private final Operations opr = new Operations( );`:
```java
	private final RecognitionInspector	recognitionInspector	= new RecognitionInspector( );
	private final TrainingInspector		trainingInspector		= new TrainingInspector( );
	private final JTabbedPane			inspectorTabs			= new JTabbedPane( );
	private final JLabel				jobStatus				= new JLabel( "Ready" );
	private final JProgressBar			jobProgress				= new JProgressBar( );
	private final JobRunner				jobs					= new JobRunner( jobStatus, jobProgress );
	private TrainingSession				trainingSession			= TrainingSession.EMPTY;
```
Replace `initialize()` and add the helpers:
```java
	private void initialize( ) {
		this.setSize( 1200, 750 );
		this.setContentPane( getMainPane( ) );
		this.setTitle( "HMM/VQ Speech Recognition - by GT" );
		jobs.register( getBtnVerify( ), getGetWordButton( ), getGetWordButton1( ), getGenerateCodeBookBtn( ), getBtnNewButton_2( ) );
	}

	/**
	 * the original controls on the left, the step inspectors on the right, a status line at the bottom
	 */
	private JPanel getMainPane( ) {
		JPanel left = getJContentPane( );
		left.setPreferredSize( new Dimension( 470, 300 ) );
		JPanel leftHolder = new JPanel( new BorderLayout( ) );
		leftHolder.add( left, BorderLayout.NORTH );
		inspectorTabs.addTab( "Recognition steps", recognitionInspector );
		inspectorTabs.addTab( "Training steps", trainingInspector );
		JPanel statusBar = new JPanel( new BorderLayout( 8, 0 ) );
		statusBar.setBorder( BorderFactory.createEmptyBorder( 2, 8, 2, 8 ) );
		statusBar.add( jobStatus, BorderLayout.CENTER );
		statusBar.add( jobProgress, BorderLayout.EAST );
		JPanel main = new JPanel( new BorderLayout( ) );
		main.add( new JSplitPane( JSplitPane.HORIZONTAL_SPLIT, leftHolder, inspectorTabs ), BorderLayout.CENTER );
		main.add( statusBar, BorderLayout.SOUTH );
		return main;
	}

	private void showRecognition( RecognitionTrace trace ) {
		recognitionInspector.show( trace );
		inspectorTabs.setSelectedComponent( recognitionInspector );
	}

	private void showTraining( ) {
		trainingInspector.show( trainingSession );
		inspectorTabs.setSelectedComponent( trainingInspector );
	}

	private void showError( Throwable t ) {
		if ( !( t instanceof IllegalStateException || t instanceof IllegalArgumentException ) ) {
			t.printStackTrace( );
		}
		JOptionPane.showMessageDialog( this, JobRunner.message( t ), "Error", JOptionPane.ERROR_MESSAGE );
	}

	/** the captured audio, or null after showing an error */
	private float[] recordedAudio( ) {
		try {
			return soundCapture.getAudioData( );
		} catch ( Exception e ) {
			showError( e );
			return null;
		}
	}

	private void reloadRegisteredWords( ) {
		getWordsComboBoxVerify( ).removeAllItems( );
		DataBase db = new ObjectIODataBase( );
		db.setType( "hmm" );
		for ( String word : db.readRegistered( ) ) {
			getWordsComboBoxVerify( ).addItem( word );
		}
	}
```
Replace the action listener bodies (keep the surrounding `if ( btnVerify == null ) { … }` structure and the `setBounds` calls):

`getBtnVerify()`:
```java
			btnVerify.addActionListener(e -> {
				if ( soundCapture.isSoundDataAvailable( ) && getWordsComboBoxVerify( ).getItemCount( ) > 0 ) {
					String expected = getWordsComboBoxVerify( ).getSelectedItem( ).toString( );
					float[] audio = recordedAudio( );
					if ( audio != null ) {
						jobs.run( "Verifying \"" + expected + "\"", progress -> opr.recognizeWithTrace( audio, expected ), trace -> {
							getStatusLblRecognize( ).setText( trace.verified( ) ? "Verified" : "<html>Not Verified<br>(heard " + trace.recognizedWord( ) + ")</html>" );
							showRecognition( trace );
						}, this::showError );
					}
				}
			});
```
`getGetWordButton()`:
```java
			getWordButton.addActionListener(arg0 -> {
				if ( soundCapture.isSoundDataAvailable( ) && getWordsComboBoxVerify( ).getItemCount( ) > 0 ) {
					float[] audio = recordedAudio( );
					if ( audio != null ) {
						jobs.run( "Recognizing the recording", progress -> opr.recognizeWithTrace( audio, null ), trace -> {
							getStatusLblRecognize( ).setText( trace.recognizedWord( ) );
							showRecognition( trace );
						}, this::showError );
					}
				}
			});
```
`getGetWordButton1()`:
```java
			getWordButton1.addActionListener(e -> {
				File f = getTestFile( );
				if ( f != null ) {
					jobs.run( "Recognizing " + f.getName( ), progress -> opr.recognizeWithTrace( f, null ), trace -> {
						getStatusLblRecognize( ).setText( trace.recognizedWord( ) );
						showRecognition( trace );
					}, this::showError );
				}
			});
```
`getGenerateCodeBookBtn()`:
```java
			generateCodeBookBtn.addActionListener(e -> jobs.run( "Generating the codebook", opr::generateCodebookWithTrace, codebook -> {
				trainingSession = trainingSession.withCodebook( codebook );
				showTraining( );
			}, this::showError ));
```
`getBtnNewButton_2()`:
```java
			btnNewButton_2.addActionListener(e -> jobs.run( "Training the word HMMs", opr::hmmTrainWithTrace, words -> {
				trainingSession = trainingSession.withWords( words );
				showTraining( );
				reloadRegisteredWords( );
			}, this::showError ));
```
In `main`, delete `test.setResizable( false );`.

- [ ] **Step 6: Runnable jar with JFreeChart**

`pom.xml`, inside `<plugins>` after the jar plugin:
```xml
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.6.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>${main.class}</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
```
Run: `mvn -q package -DskipTests && unzip -l target/speech-recognition-hmm-vq-mfcc-1.0-SNAPSHOT.jar | grep -c "org/jfree/chart/JFreeChart.class" && unzip -p target/speech-recognition-hmm-vq-mfcc-1.0-SNAPSHOT.jar META-INF/MANIFEST.MF | grep Main-Class`
Expected: `1` and `Main-Class: org.ioe.tprsa.ui.HMM_VQ_Speech_Recognition`.

- [ ] **Step 7: README**

In `README.md`, under "Main classes to look into are :", replace the GUI bullet with:
```markdown
- `org.ioe.tprsa.ui.HMM_VQ_Speech_Recognition` : GUI to record voice samples per word, train, and test with a just recorded sample or a saved .wav file.
  The right half of the window shows every step of the algorithm for the most recent job: *Recognition steps* (waveform and end point
  detection, framing and windowing, spectrum and mel filter bank, MFCC, deltas and energy, vector quantization, the score of every word
  model and the best state path) and *Training steps* (LBG codebook, training sequences, Baum-Welch convergence, learned matrices,
  summary). Charts show exact values on hover, zoom by dragging and can be saved as PNG (right click).
```

- [ ] **Step 8: Manual check**

Run from the project root: `java -jar target/speech-recognition-hmm-vq-mfcc-1.0-SNAPSHOT.jar`
Check: window resizable; "Recognize a Saved WAV File" with `TrainWav/Ship/Ship3.wav` shows all 8 recognition steps, the frame slider moves the cursors, step 8's word picker switches paths; record a word and press Verify; "Generate CodeBook" then "Train HMM" (in a copy of the project, or accept that `models/` is rewritten with identical files) fill the Training steps; the window stays responsive and the status line shows progress; recognising before training in an empty folder shows the "Train first" dialog.

- [ ] **Step 9: Checkpoint**

Run: `mvn -q test`; Expected: all pass.

---

## Self-Review Notes

- Spec coverage: data model (Tasks 2–6), API changes incl. `Operations(Path)` (1, 5, 6), 8 recognition views (8, 9), 5 training views (10), inspectors with frame slider and word picker (11), threading, status, error table (5, 6, 12), runnable jar (12), all tests listed in the spec's Testing section (1–12).
- Deliberate deviations from the spec, recorded in the tasks: k-means distortion is checked as "last ≤ first per split, decreasing across splits" (Task 6 note); codebook progress is reported per phase rather than per split.
- Not in scope (from the spec): live plots, run history, gesture project. Noticed but untouched: the Add Sample tab's Record button is created but never added to the panel.
