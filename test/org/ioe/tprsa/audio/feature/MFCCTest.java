package org.ioe.tprsa.audio.feature;

import org.ioe.tprsa.TestSignals;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class MFCCTest {

	private static final int SPF = 512;

	private final MFCC mfcc = new MFCC( SPF, TestSignals.SAMPLING_RATE, 12 );

	@Test
	void producesRequestedNumberOfFiniteCoefficients( ) {
		double[] c = mfcc.doMFCC( TestSignals.sine( SPF, 1000, 0.5 ) );
		assertEquals( 12, c.length );
		for ( double v : c ) {
			assertTrue( Double.isFinite( v ) );
		}
	}

	@Test
	void differentTonesGiveDifferentCoefficients( ) {
		double[] low = mfcc.doMFCC( TestSignals.sine( SPF, 300, 0.5 ) );
		double[] high = mfcc.doMFCC( TestSignals.sine( SPF, 4000, 0.5 ) );
		double dist = 0;
		for ( int i = 0; i < low.length; i++ ) {
			dist += ( low[ i ] - high[ i ] ) * ( low[ i ] - high[ i ] );
		}
		assertTrue( Math.sqrt( dist ) > 1.0, "distance was " + Math.sqrt( dist ) );
	}

	@Test
	void melScaleReferencePoints( ) {
		assertEquals( 0.0, mfcc.freqToMel( 0 ), 1e-9 );
		assertEquals( 1000.0, mfcc.freqToMel( 1000 ), 1.0 );
	}

	@Test
	void melFiltersAreTrianglesFromNeighbourToNeighbourCentre( ) throws Exception {
		Method binIdx = MFCC.class.getDeclaredMethod( "fftBinIndices" );
		Method filter = MFCC.class.getDeclaredMethod( "melFilter", double[].class, int[].class );
		binIdx.setAccessible( true );
		filter.setAccessible( true );
		int[] cbin = ( int[] ) binIdx.invoke( mfcc );
		for ( int k = 1; k + 1 < cbin.length; k++ ) {
			double[] atCentre = new double[ SPF ], atPrev = new double[ SPF ], atNext = new double[ SPF ];
			atCentre[ cbin[ k ] ] = 1;
			atPrev[ cbin[ k - 1 ] ] = 1;
			atNext[ cbin[ k + 1 ] ] = 1;
			assertEquals( 1.0, ( ( double[] ) filter.invoke( mfcc, atCentre, cbin ) )[ k - 1 ], 1e-12, "peak of filter " + k );
			if ( cbin[ k - 1 ] < cbin[ k ] ) {
				assertEquals( 0.0, ( ( double[] ) filter.invoke( mfcc, atPrev, cbin ) )[ k - 1 ], 1e-12, "start of filter " + k );
			}
			assertEquals( 0.0, ( ( double[] ) filter.invoke( mfcc, atNext, cbin ) )[ k - 1 ], 1e-12, "end of filter " + k );
		}
	}

	@Test
	void melFilterRisingEdgeHasNonZeroWeight( ) throws Exception {
		Method binIdx = MFCC.class.getDeclaredMethod( "fftBinIndices" );
		Method filter = MFCC.class.getDeclaredMethod( "melFilter", double[].class, int[].class );
		binIdx.setAccessible( true );
		filter.setAccessible( true );
		int[] cbin = ( int[] ) binIdx.invoke( mfcc );

		// pick a filter wide enough to have a bin strictly between its start and centre
		int k = cbin.length - 2;
		int between = ( cbin[ k - 1 ] + cbin[ k ] ) / 2;
		assertTrue( between > cbin[ k - 1 ] && between < cbin[ k ] );

		double[] spectrum = new double[ SPF ];
		spectrum[ between ] = 1.0;
		double[] fbank = ( double[] ) filter.invoke( mfcc, spectrum, cbin );
		assertTrue( fbank[ k - 1 ] > 0, "filter " + k + " ignores a bin on its rising edge" );
	}
}
