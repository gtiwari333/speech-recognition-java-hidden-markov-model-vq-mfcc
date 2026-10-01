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
