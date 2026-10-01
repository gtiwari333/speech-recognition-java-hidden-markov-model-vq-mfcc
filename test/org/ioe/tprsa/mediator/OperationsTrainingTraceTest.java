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
			// only last <= first: the distortion sums plain distances while k-means minimises squared distances, so it need not fall at every iteration
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
			// bundled words: only Hello converges (27 iterations); the others run into the iteration limit
			assertEquals( "Hello".equals( w.word( ) ), w.converged( ), w.word( ) );
			if ( !w.converged( ) ) {
				assertEquals( HiddenMarkov.MAX_ITERATIONS, w.iterations( ), w.word( ) + " stopped at the limit" );
			}
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
