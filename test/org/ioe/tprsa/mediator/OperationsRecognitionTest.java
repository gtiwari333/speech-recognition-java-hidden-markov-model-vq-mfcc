package org.ioe.tprsa.mediator;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * end-to-end regression test: recognise the bundled TrainWav recordings with the committed
 * models/codeBook/codebook.cbk and models/HMM/*.hmm. Needs the project root as working directory.
 * <p>
 * Baseline: 39 / 39 with the models retrained after the feature / Baum-Welch fixes (the original models
 * scored 36 / 39). These are the training recordings, so this only guards against regressions; held-out
 * 3-fold cross validation is ~80%. Retrain the models whenever the feature pipeline changes, otherwise this
 * test will (rightly) fail.
 */
@Tag( "integration" )
class OperationsRecognitionTest {

	private static final double MIN_ACCURACY = 0.9;

	@Test
	void recognisesTrainingRecordings( ) throws Exception {
		File root = new File( "TrainWav" );
		assertTrue( root.isDirectory( ), "run from the project root" );

		Operations op = new Operations( );
		int total = 0, correct = 0;
		List< String > misses = new ArrayList<>( );
		for ( File wordDir : root.listFiles( File::isDirectory ) ) {
			for ( File wav : wordDir.listFiles( ( d, name ) -> name.endsWith( ".wav" ) ) ) {
				String recognised = op.hmmGetWordFromFile( wav );
				total++;
				if ( recognised.equals( wordDir.getName( ) ) ) {
					correct++;
				} else {
					misses.add( wav.getName( ) + " -> " + recognised );
				}
			}
		}
		double accuracy = ( double ) correct / total;
		assertTrue( accuracy >= MIN_ACCURACY, correct + "/" + total + " correct, misses: " + misses );
	}

	@Test
	void knowsRegisteredWords( ) {
		Operations op = new Operations( );
		assertTrue( op.checkWord( "apple" ), "lookup is case-insensitive" );
		assertFalse( op.checkWord( "banana" ) );
	}
}
