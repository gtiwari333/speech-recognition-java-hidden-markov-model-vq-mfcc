package org.ioe.tprsa.audio;

import org.ioe.tprsa.TestSignals;
import org.ioe.tprsa.audio.feature.MFCC;
import org.ioe.tprsa.trace.FeatureTrace;
import org.ioe.tprsa.trace.MfccFrame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class FeatureTraceTest {

	private static final int RATE = TestSignals.SAMPLING_RATE;
	private static final int SPF = 512;

	private static FeatureExtract extract( ) {
		PreProcess p = new PreProcess( TestSignals.silenceToneSilence( RATE / 2, RATE / 2, 4 ), SPF, RATE );
		FeatureExtract fe = new FeatureExtract( p.framedSignal, p.rawFramedSignal, RATE, SPF );
		fe.makeMfccFeatureVector( );
		return fe;
	}

	@Test
	void recordsEveryIntermediateArray( ) {
		FeatureExtract fe = extract( );
		FeatureTrace t = fe.toTrace( );
		int n = t.frameCount( );
		assertEquals( fe.getFeatureVector( ).getNoOfFrames( ), n );
		assertEquals( SPF / 2 + 1, t.magnitudeSpectra( )[ 0 ].length );
		assertEquals( 30, t.logMelEnergies( )[ 0 ].length );
		for ( double[][] m : new double[][][] { t.mfccBeforeCmn( ), t.mfcc( ), t.deltaMfcc( ), t.deltaDeltaMfcc( ) } ) {
			assertEquals( n, m.length );
			assertEquals( 12, m[ 0 ].length );
		}
		assertEquals( n, t.logEnergy( ).length );
		assertEquals( n, t.deltaLogEnergy( ).length );
		assertEquals( n, t.deltaDeltaLogEnergy( ).length );
		assertArrayEquals( fe.getFeatureVector( ).getFeatureVector( )[ 3 ], t.featureVectors( )[ 3 ] );
		assertArrayEquals( t.deltaMfcc( )[ 3 ], Arrays.copyOfRange( t.featureVectors( )[ 3 ], 12, 24 ) );
		assertEquals( t.logEnergy( )[ 3 ], t.featureVectors( )[ 3 ][ 36 ] );
		assertEquals( 32, t.melCentreBins( ).length );
	}

	@Test
	void mfccIsTheMeanNormalisedRawCepstrum( ) {
		FeatureTrace t = extract( ).toTrace( );
		for ( int c = 0; c < 12; c++ ) {
			double mean = 0;
			for ( double[] frame : t.mfccBeforeCmn( ) ) {
				mean += frame[ c ];
			}
			mean /= t.frameCount( );
			for ( int f = 0; f < t.frameCount( ); f++ ) {
				assertEquals( t.mfccBeforeCmn( )[ f ][ c ] - mean, t.mfcc( )[ f ][ c ], 1e-9 );
			}
		}
	}

	@Test
	void computeFrameMatchesDoMfcc( ) {
		MFCC mfcc = new MFCC( SPF, RATE, 12 );
		float[] frame = TestSignals.sine( SPF, 1000, 0.5 );
		MfccFrame f = mfcc.computeFrame( frame );
		assertArrayEquals( mfcc.doMFCC( frame ), f.cepstra( ), 0 );
		assertEquals( SPF / 2 + 1, f.magnitudeSpectrum( ).length );
		int peak = 0;
		for ( int k = 1; k < f.magnitudeSpectrum( ).length; k++ ) {
			peak = f.magnitudeSpectrum( )[ k ] > f.magnitudeSpectrum( )[ peak ] ? k : peak;
		}
		assertEquals( 1000.0 * SPF / RATE, peak, 1, "spectrum peak at 1000 Hz" );
	}

	@Test
	void toTraceBeforeExtractionFails( ) {
		PreProcess p = new PreProcess( TestSignals.silenceToneSilence( RATE / 2, RATE / 2, 5 ), SPF, RATE );
		FeatureExtract fe = new FeatureExtract( p.framedSignal, p.rawFramedSignal, RATE, SPF );
		assertThrows( IllegalStateException.class, fe::toTrace );
	}
}
