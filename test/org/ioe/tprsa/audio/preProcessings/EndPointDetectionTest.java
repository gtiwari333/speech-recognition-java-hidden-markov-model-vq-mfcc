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
