package org.ioe.tprsa.audio;

import org.ioe.tprsa.TestSignals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreProcessTest {

	private static final int RATE = TestSignals.SAMPLING_RATE;
	private static final int SPF = 512;

	@Test
	void framesWithHalfOverlap( ) {
		PreProcess p = new PreProcess( TestSignals.silenceToneSilence( RATE / 2, RATE / 2, 1 ), SPF, RATE );

		int expectedFrames = 2 * p.afterEndPtDetection.length / SPF - 1;
		assertEquals( expectedFrames, p.noOfFrames );
		assertEquals( expectedFrames, p.framedSignal.length );
		for ( float[] frame : p.framedSignal ) {
			assertEquals( SPF, frame.length );
		}
	}

	@Test
	void hammingWindowMatchesHtk( ) {
		PreProcess p = new PreProcess( TestSignals.silenceToneSilence( RATE / 2, RATE / 2, 1 ), SPF, RATE );
		// HTK Book eq. 5.2: 0.54 - 0.46 cos(2 pi (n - 1) / (N - 1)), n = 1..N: symmetric, 0.08 at both ends
		for ( int n = 1; n <= SPF; n++ ) {
			assertEquals( 0.54 - 0.46 * Math.cos( 2 * Math.PI * ( n - 1 ) / ( SPF - 1 ) ), p.hammingWindow[ n ], 1e-6 );
		}
		assertEquals( p.hammingWindow[ 1 ], p.hammingWindow[ SPF ], 1e-6 );
	}

	@Test
	void preEmphasisIsAppliedBeforeWindowing( ) {
		PreProcess p = new PreProcess( TestSignals.silenceToneSilence( RATE / 2, RATE / 2, 1 ), SPF, RATE );
		float[] raw = p.rawFramedSignal[ 0 ], frame = p.framedSignal[ 0 ];
		float k = PreProcess.PRE_EMPHASIS;
		// HTK Book: s'_n = s_n - k s_(n-1) (eq. 5.1), then the Hamming window; s'_1 = (1 - k) s_1
		assertEquals( ( 1 - k ) * raw[ 0 ] * p.hammingWindow[ 1 ], frame[ 0 ], 1e-6 );
		for ( int j = 1; j < SPF; j++ ) {
			assertEquals( ( raw[ j ] - k * raw[ j - 1 ] ) * p.hammingWindow[ j + 1 ], frame[ j ], 1e-5, "sample " + j );
		}
		assertArrayEquals( java.util.Arrays.copyOfRange( p.afterEndPtDetection, 0, SPF ), raw, "raw frames are unmodified" );
	}

	@Test
	void normalizesToUnitPeakWithoutTouchingInput( ) {
		float[] signal = TestSignals.silenceToneSilence( RATE / 2, RATE / 2, 1 );
		float[] copy = signal.clone( );
		PreProcess p = new PreProcess( signal, SPF, RATE );
		assertArrayEquals( copy, signal );
		float peak = 0;
		for ( float v : p.originalSignal ) {
			peak = Math.max( peak, Math.abs( v ) );
		}
		assertEquals( 1.0, peak, 1e-6 );
	}

	@Test
	void normalizesWhenFirstSampleIsTheNegativePeak( ) {
		float[] signal = TestSignals.silenceToneSilence( RATE / 2, RATE / 2, 1 );
		signal[ 0 ] = -5000;
		PreProcess p = new PreProcess( signal, SPF, RATE );
		assertEquals( -1.0, p.originalSignal[ 0 ], 1e-6 );
	}

	@Test
	void signalShorterThanOneFrameGivesOneZeroPaddedFrame( ) {
		PreProcess p = new PreProcess( TestSignals.sine( SPF / 3, 440, 1000 ), SPF, RATE );
		assertEquals( 1, p.noOfFrames );
		assertEquals( SPF, p.framedSignal[ 0 ].length );
	}

	@Test
	void silentInputDoesNotProduceNaN( ) {
		PreProcess p = new PreProcess( new float[ RATE / 2 ], SPF, RATE );
		for ( float[] frame : p.framedSignal ) {
			for ( float v : frame ) {
				assertFalse( Float.isNaN( v ) );
			}
		}
	}
}
