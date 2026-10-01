package org.ioe.tprsa.audio.preProcessings;

import org.ioe.tprsa.TestSignals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EndPointDetectionTest {

	private static final int RATE = TestSignals.SAMPLING_RATE;
	private static final int SAMPLES_PER_MS = RATE / 1000;

	@Test
	void removesLeadingAndTrailingSilence( ) {
		int silence = RATE / 2, tone = RATE / 2;
		float[] signal = TestSignals.silenceToneSilence( silence, tone, 42 );

		float[] voiced = new EndPointDetection( signal, RATE ).doEndPointDetection( );

		// allow a handful of 1ms frames of slack on either side
		assertEquals( tone, voiced.length, 20 * SAMPLES_PER_MS );
		assertTrue( voiced.length < signal.length / 2 );
	}

	/**
	 * like the recorder's files: 100 ms of digital silence (exact zeros), speech starting right after it, then
	 * background noise. The first 200 ms are half speech, so they cannot be used as the noise estimate.
	 */
	@Test
	void speechStartingInsideTheFirst200msIsKept( ) {
		int tone = RATE / 2;
		float[] speech = TestSignals.sine( tone, 440, 1000 );
		float[] background = TestSignals.noise( tone, 5, 11 );
		for ( int i = 0; i < tone; i++ ) {
			speech[ i ] += background[ i ];
		}
		float[] signal = TestSignals.concat( new float[ RATE / 10 ], speech, TestSignals.noise( RATE / 2, 5, 12 ) );

		EndPointDetection epd = new EndPointDetection( signal, RATE );
		float[] voiced = epd.doEndPointDetection( );

		assertFalse( epd.isWholeSignalUsed( ) );
		assertEquals( tone, voiced.length, 20 * SAMPLES_PER_MS, "the whole tone is kept" );
		assertTrue( epd.getNoiseSd( ) < 20, "noise estimated from the background, not the speech: σ = " + epd.getNoiseSd( ) );
		boolean[] noise = epd.getNoiseFrames( );
		for ( int f = 0; f < 10; f++ ) {
			assertFalse( noise[ f ], "digital silence frame " + f + " is not used as noise" );
		}
	}

	@Test
	void keptSamplesComeFromTheTone( ) {
		float[] signal = TestSignals.silenceToneSilence( RATE / 2, RATE / 2, 7 );
		float[] voiced = new EndPointDetection( signal, RATE ).doEndPointDetection( );
		double energy = 0;
		for ( float v : voiced ) {
			energy += v * v;
		}
		double rms = Math.sqrt( energy / voiced.length );
		// tone amplitude 1000 -> rms ~707; background noise rms is 5
		assertTrue( rms > 500, "rms was " + rms );
	}

	@Test
	void shortInputDoesNotThrow( ) {
		float[] shortSignal = TestSignals.sine( RATE / 10, 440, 1000 );
		assertDoesNotThrow( ( ) -> new EndPointDetection( shortSignal, RATE ).doEndPointDetection( ) );
	}

	@Test
	void outputHasNoTrailingZeroFrame( ) {
		float[] signal = TestSignals.silenceToneSilence( RATE / 2, RATE / 2, 3 );
		float[] voiced = new EndPointDetection( signal, RATE ).doEndPointDetection( );
		boolean allZero = true;
		for ( int i = voiced.length - SAMPLES_PER_MS; i < voiced.length; i++ ) {
			allZero &= voiced[ i ] == 0f;
		}
		assertFalse( allZero );
	}
}
