/*
  Please feel free to use/modify this class. 
  If you give me credit by keeping this information or
  by sending me an email before using it or by reporting bugs , i will be happy.
  Email : gtiwari333@gmail.com,
  Blog : http://ganeshtiwaridotcomdotnp.blogspot.com/ 
 */
package org.ioe.tprsa.mediator;

import org.ioe.tprsa.audio.FeatureExtract;
import org.ioe.tprsa.audio.FormatControlConf;
import org.ioe.tprsa.audio.PreProcess;
import org.ioe.tprsa.audio.WaveData;
import org.ioe.tprsa.audio.feature.FeatureVector;
import org.ioe.tprsa.classify.speech.HiddenMarkov;
import org.ioe.tprsa.classify.speech.vq.Codebook;
import org.ioe.tprsa.classify.speech.vq.Points;
import org.ioe.tprsa.db.DataBase;
import org.ioe.tprsa.db.ObjectIODataBase;
import org.ioe.tprsa.db.TrainingTestingWaveFiles;
import org.ioe.tprsa.trace.CodebookTrace;
import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.trace.VqTrace;
import org.ioe.tprsa.trace.WordScore;
import org.ioe.tprsa.trace.WordTrainingTrace;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * @author Ganesh Tiwari
 */
public class Operations {

	TrainingTestingWaveFiles	trainTestWavs;
	final FormatControlConf			fc					= new FormatControlConf( );
	final int							samplingRate		= ( int ) fc.getRate( );
	// int samplePerFrame = 256;//16ms for 8 khz
	final int							samplePerFrame		= 512;							// 23.22ms
	final int							FEATUREDIMENSION	= 39;
	static final int					NUM_STATES			= 6;
	static final int					CODEBOOK_SIZE		= 256;
	List< String >				words;
	String[]					users;
	File[][]					wavFiles;
	FeatureExtract				fExt;
	final WaveData					wd;
	PreProcess					prp;
	Codebook					cb;
	HiddenMarkov				mkv;
	DataBase					db;

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

	public String hmmGetWordFromFileByteArray( byte[] byteArray ) throws Exception {
		// extract features
		FeatureVector feature = extractFeatureFromFileByteArray( byteArray );
		return hmmGetWordWithFeature( feature );
	}

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

	/**
	 * @param byteArray
	 * @return
	 * @throws Exception
	 */
	public FeatureVector extractFeatureFromFileByteArray( byte[] byteArray ) throws Exception {
		float[] arrAmp;
		arrAmp = wd.extractAmplitudeFromFileByteArray( byteArray );
		return extractFeatureFromExtractedAmplitureByteArray( arrAmp );
	}

	/**
	 * @return
	 * @throws Exception
	 */
	public FeatureVector extractFeatureFromExtractedAmplitureByteArray( float[] arrAmp ) {
		prp = new PreProcess( arrAmp, samplePerFrame, samplingRate );
		fExt = new FeatureExtract( prp.framedSignal, prp.rawFramedSignal, samplingRate, samplePerFrame );
		fExt.makeMfccFeatureVector( );
		return fExt.getFeatureVector( );
	}

	/**
	 * @param speechFile
	 * @return
	 * @throws Exception
	 */
	private FeatureVector extractFeatureFromFile( File speechFile ) throws Exception {
		float[] arrAmp;
		arrAmp = wd.extractAmplitudeFromFile( speechFile );
		return extractFeatureFromExtractedAmplitureByteArray( arrAmp );
	}

	/**
	 * @param features
	 * @return
	 */
	private Points[] getPointsFromFeatureVector( FeatureVector features ) {
		// get Points object from all feature vector
		Points[] pts = new Points[ features.getFeatureVector( ).length ];
		for ( int j = 0; j < features.getFeatureVector( ).length; j++ ) {
			pts[ j ] = new Points( features.getFeatureVector( )[ j ] );
		}
		return pts;
	}

	/**
	 * @param word
	 * @return
	 */
	public boolean checkWord( String word ) {
		db = new ObjectIODataBase( baseDir );
		db.setType( "hmm" );
		words = db.readRegistered( );
		for (String s : words) {
			if (s.equalsIgnoreCase(word)) {
				return true;// word found
			}
		}
		return false;// word not found
	}

	/**
	 * @return
	 */
	public boolean checkSelectedPath( ) {
		return true;
	}

	double[][] test0 =																																																																																	// user0
							{ { 1.0, 2.5, 5.0, 10, 3.0, 8.0, 4.0, 45.0 }, { 1.0, 2.0, 4.0, 10, 3.0, 9.0, 3.5, 52.0 }, { 1.0, 2.2, 5.0, 11, 3.0, 9.0, 4.0, 52.0 }, { 1.0, 3.0, 5.0, 9, 3.0, 10.0, 3.1, 51.0 }, { 1.0, 2.0, 6.0, 10, 3.0, 9.0, 4.0, 54.0 }, { 1.0, 2.2, 5.0, 12, 3.0, 8.0, 4.0, 52.0 } };
	double[][] test1 =																																																																																	// user1
							{ { 2.0, 2.5, 5.0, 20, 3.0, 18.0, 4.0, 150.0 }, { 2.0, 2.0, 4.0, 19, 3.0, 19.0, 3.5, 142.0 }, { 2.0, 2.5, 5.0, 20, 3.0, 19.0, 4.0, 150.0 }, { 2.0, 3.0, 5.0, 20, 3.0, 18.0, 3.1, 151.0 }, { 2.0, 2.0, 6.0, 20, 3.0, 19.0, 4.0, 150.0 }, { 2.0, 2.7, 5.0, 22, 3.0, 18.0, 4.0, 145.0 } };
	double[][] test2 =																																																																																	// suer
							{ { 12.0, 2.5, 5.0, 30, 13.0, 18.0, 4.0, 10.0 }, { 10.0, 2.0, 4.0, 30, 13.0, 19.0, 3.5, 12.0 }, { 12.0, 2.5, 5.0, 33, 13.0, 19.0, 4.0, 10.0 }, { 9.0, 3.0, 5.0, 30, 13.0, 18.0, 3.1, 11.0 }, { 11.0, 2.0, 6.0, 30, 13.0, 19.0, 4.0, 10.0 }, { 12.0, 2.7, 5.0, 31, 13.0, 18.0, 4.0, 12.0 } };
	double[][] test3 =																																																																																	// suer
							{ { 322.0, 2.5, 5.0, 30, 303.0, 18.0, 4.0, 300.0 }, { 312.0, 2.0, 4.0, 30, 353.0, 18.0, 3.5, 312.0 }, { 312.0, 2.5, 5.0, 30, 313.0, 19.0, 4.0, 300.0 }, { 322.0, 3.0, 5.0, 30, 303.0, 16.0, 3.1, 311.0 }, { 312.0, 2.0, 6.0, 30, 313.0, 19.0, 4.0, 300.0 }, { 332.0, 2.7, 5.0, 30, 313.0, 12.0, 4.0, 302.0 } };
	double[][] test4 = { { 412.0, 2.5, 5.0, 30, 400.0, 18.0, 41.0, 14.0 }, { 412.0, 2.0, 8.0, 30, 413.0, 19.0, 43.5, 12.0 }, { 400.0, 1.5, 5.0, 30, 413.0, 19.0, 44.0, 9.0 }, { 412.0, 3.0, 3.0, 30, 413.0, 18.0, 43.1, 11.0 }, { 412.0, 2.0, 6.0, 30, 433.0, 19.0, 44.0, 15.0 }, { 400.0, 1.7, 9.0, 30, 433.0, 28.0, 40.0, 12.0 } };
	double[][][] train = { {																																																																																// user0
									{ 1.0, 2.5, 5.0, 10, 3.0, 8.0, 4.0, 50.0 }, { 1.0, 2.0, 4.0, 10, 3.0, 9.0, 3.5, 52.0 }, { 1.0, 2.5, 5.0, 10, 3.0, 9.0, 4.0, 40.0 }, { 1.0, 3.0, 5.0, 10, 3.0, 10.0, 3.1, 51.0 }, { 1.0, 2.0, 6.0, 10, 3.0, 9.0, 4.0, 50.0 }, { 1.0, 2.7, 5.0, 10, 3.0, 8.0, 4.0, 59.0 } }, {											// user1
									{ 2.0, 2.5, 5.0, 20, 3.0, 18.0, 4.0, 150.0 }, { 2.0, 2.0, 4.0, 20, 3.0, 19.0, 3.5, 152.0 }, { 2.0, 2.5, 5.0, 20, 3.0, 19.0, 4.0, 150.0 }, { 2.0, 3.0, 5.0, 20, 3.0, 18.0, 3.1, 151.0 }, { 2.0, 2.0, 6.0, 20, 3.0, 19.0, 4.0, 150.0 }, { 2.0, 2.7, 5.0, 20, 3.0, 18.0, 4.0, 152.0 } }, {									// user2
									{ 12.0, 2.5, 5.0, 30, 13.0, 18.0, 4.0, 10.0 }, { 12.0, 2.0, 4.0, 30, 13.0, 19.0, 3.5, 12.0 }, { 12.0, 2.5, 5.0, 30, 13.0, 19.0, 4.0, 10.0 }, { 12.0, 3.0, 5.0, 30, 13.0, 18.0, 3.1, 11.0 }, { 12.0, 2.0, 6.0, 30, 13.0, 19.0, 4.0, 10.0 }, { 12.0, 2.7, 5.0, 30, 13.0, 18.0, 4.0, 12.0 } }, {							// user3
									{ 312.0, 2.5, 5.0, 30, 313.0, 18.0, 4.0, 310.0 }, { 312.0, 2.0, 4.0, 30, 313.0, 19.0, 3.5, 312.0 }, { 312.0, 2.5, 5.0, 30, 313.0, 19.0, 4.0, 310.0 }, { 312.0, 3.0, 5.0, 30, 313.0, 18.0, 3.1, 311.0 }, { 312.0, 2.0, 6.0, 30, 313.0, 19.0, 4.0, 310.0 }, { 312.0, 2.7, 5.0, 30, 313.0, 18.0, 4.0, 312.0 } }, {			// user4
									{ 412.0, 2.5, 5.0, 30, 413.0, 18.0, 44.0, 10.0 }, { 412.0, 2.0, 4.0, 30, 413.0, 19.0, 43.5, 12.0 }, { 412.0, 2.5, 5.0, 30, 413.0, 19.0, 44.0, 10.0 }, { 412.0, 3.0, 5.0, 30, 413.0, 18.0, 43.1, 11.0 }, { 412.0, 2.0, 6.0, 30, 413.0, 19.0, 44.0, 10.0 }, { 412.0, 2.7, 5.0, 30, 413.0, 18.0, 44.0, 12.0 } }, {			// user5
									{ 152.0, 52.5, 55.0, 30, 13.0, 18.0, 4.0, 10.0 }, { 152.0, 52.0, 54.0, 30, 13.0, 19.0, 3.5, 12.0 }, { 152.0, 52.5, 55.0, 30, 13.0, 19.0, 4.0, 10.0 }, { 152.0, 53.0, 55.0, 30, 13.0, 18.0, 3.1, 11.0 }, { 152.0, 52.0, 56.0, 30, 13.0, 19.0, 4.0, 10.0 }, { 152.0, 52.7, 55.0, 30, 13.0, 18.0, 4.0, 12.0 } }, {			// suer6
									{ 162.0, 2.5, 56.0, 30, 13.0, 18.0, 64.0, 10.0 }, { 162.0, 2.0, 46.0, 30, 13.0, 19.0, 63.5, 12.0 }, { 162.0, 2.5, 56.0, 30, 13.0, 19.0, 64.0, 10.0 }, { 162.0, 3.0, 56.0, 30, 13.0, 18.0, 63.1, 11.0 }, { 162.0, 2.0, 66.0, 30, 13.0, 19.0, 64.0, 10.0 }, { 162.0, 2.7, 56.0, 30, 13.0, 18.0, 64.0, 12.0 } }, {			// user7
									{ 12.0, 72.5, 5.0, 30, 13.0, 18.0, 74.0, 170.0 }, { 12.0, 72.0, 4.0, 30, 19.0, 19.0, 73.5, 172.0 }, { 12.0, 72.5, 5.0, 30, 13.0, 19.0, 74.0, 170.0 }, { 12.0, 73.0, 5.0, 30, 18.0, 18.0, 73.1, 171.0 }, { 12.0, 72.0, 6.0, 30, 13.0, 19.0, 74.0, 170.0 }, { 12.0, 72.7, 5.0, 30, 13.0, 18.0, 74.0, 172.0 } }, {			// user8
									{ 12.0, 82.5, 5.0, 30, 823.0, 11.0, 42.0, 180.0 }, { 12.0, 82.0, 4.0, 30, 813.0, 19.0, 32.5, 182.0 }, { 12.0, 85.5, 5.0, 30, 823.0, 20.0, 42.0, 180.0 }, { 12.0, 83.0, 6.0, 30, 813.0, 18.0, 32.1, 188.0 }, { 12.0, 82.0, 6.0, 30, 813.0, 19.0, 42.0, 180.0 }, { 12.0, 82.7, 5.0, 30, 823.0, 21.0, 42.0, 182.0 } }, };
	// ///////////////////////////////////////////////////////////////////////////////////
}
