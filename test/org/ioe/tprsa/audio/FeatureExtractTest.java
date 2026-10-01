package org.ioe.tprsa.audio;

import org.ioe.tprsa.TestSignals;
import org.ioe.tprsa.audio.feature.FeatureVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeatureExtractTest {

	private static final int RATE = TestSignals.SAMPLING_RATE;
	private static final int SPF = 512;

	private static FeatureVector extract( long seed ) {
		PreProcess p = new PreProcess( TestSignals.silenceToneSilence( RATE / 2, RATE / 2, seed ), SPF, RATE );
		FeatureExtract fe = new FeatureExtract( p.framedSignal, RATE, SPF );
		fe.makeMfccFeatureVector( );
		return fe.getFeatureVector( );
	}

	@Test
	void producesThirtyNineDimensionalFeatures( ) {
		FeatureVector fv = extract( 1 );
		assertEquals( 39, fv.getNoOfFeatures( ) );
		assertTrue( fv.getNoOfFrames( ) > 0 );
		assertEquals( 12, fv.getMfccFeature( )[ 0 ].length );
	}

	@Test
	void allFeaturesAreFinite( ) {
		for ( double[] frame : extract( 2 ).getFeatureVector( ) ) {
			for ( double v : frame ) {
				assertTrue( Double.isFinite( v ) );
			}
		}
	}

	@Test
	void shortRecordingStillGivesFiniteFeatures( ) {
		// shorter than the 200 ms noise estimate window and than the delta regression window
		PreProcess p = new PreProcess( TestSignals.silenceToneSilence( RATE / 20, RATE / 20, 9 ), SPF, RATE );
		FeatureExtract fe = new FeatureExtract( p.framedSignal, RATE, SPF );
		fe.makeMfccFeatureVector( );
		for ( double[] frame : fe.getFeatureVector( ).getFeatureVector( ) ) {
			for ( double v : frame ) {
				assertTrue( Double.isFinite( v ) );
			}
		}
	}

	@Test
	void cepstralMeanIsRemoved( ) {
		double[][] mfcc = extract( 3 ).getMfccFeature( );
		for ( int c = 0; c < 12; c++ ) {
			double mean = 0;
			for ( double[] frame : mfcc ) {
				mean += frame[ c ];
			}
			mean /= mfcc.length;
			assertEquals( 0.0, mean, 1e-9, "coefficient " + c );
		}
	}
}
