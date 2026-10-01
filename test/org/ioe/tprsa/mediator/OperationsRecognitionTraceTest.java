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
	 * with the committed models every training recording is recognised as its folder's word (39 / 39 since the
	 * end point detection fix). Update this if models/ is retrained.
	 */
	@Test
	void recognitionResultsAreUnchanged( ) throws Exception {
		Operations op = new Operations( );
		for ( File f : trainingFiles( ) ) {
			String expected = f.getParentFile( ).getName( );
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
			assertEquals( 0, s.statePath( )[ 0 ], "starts in the first state" );
			for ( int f = 1; f < frames; f++ ) {
				int step = s.statePath( )[ f ] - s.statePath( )[ f - 1 ];
				assertTrue( step >= 0 && step <= 2, s.word( ) + " frame " + f + ": left-to-right, skips at most one state" );
			}
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
		// a Zebra recording verified as "Ship" must fail
		RecognitionTrace miss = new Operations( ).recognizeWithTrace( new File( "TrainWav/Zebra/Zebra0.wav" ), "Ship" );
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
