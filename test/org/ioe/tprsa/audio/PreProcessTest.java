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
	void appliesHammingWindow( ) {
		PreProcess p = new PreProcess( TestSignals.silenceToneSilence( RATE / 2, RATE / 2, 1 ), SPF, RATE );
		// window[j+1] = 0.54 - 0.46 cos(2*pi*(j+1)/N): ~0.08 at the edge, 1.0 in the middle
		float[] frame = p.framedSignal[ 0 ];
		int mid = SPF / 2 - 1;
		float rawEdge = p.afterEndPtDetection[ SPF - 1 ];
		float rawMid = p.afterEndPtDetection[ mid ];
		assertEquals( rawEdge * 0.08, frame[ SPF - 1 ], 1e-4 );
		assertEquals( rawMid * 1.0, frame[ mid ], 1e-4 );
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
