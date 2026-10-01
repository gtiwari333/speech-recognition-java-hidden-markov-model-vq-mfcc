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
	private static RecognitionTrace	recognized;
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

	/** verification of Developer2.wav as "Developer": correctly recognised, 90 frames */
	static synchronized RecognitionTrace recognized( ) throws Exception {
		if ( recognized == null ) {
			recognized = new Operations( ).recognizeWithTrace( new File( "TrainWav/Developer/Developer2.wav" ), "Developer" );
		}
		return recognized;
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
