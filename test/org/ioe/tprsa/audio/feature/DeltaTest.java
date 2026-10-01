package org.ioe.tprsa.audio.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeltaTest {

	private static double[][] ramp2D( int frames, int coeffs ) {
		double[][] d = new double[ frames ][ coeffs ];
		for ( int t = 0; t < frames; t++ ) {
			for ( int c = 0; c < coeffs; c++ ) {
				d[ t ][ c ] = ( c + 1 ) * t;
			}
		}
		return d;
	}

	@Test
	void delta2DOfConstantIsZeroInInterior( ) {
		double[][] data = new double[ 10 ][ 3 ];
		for ( double[] row : data ) {
			java.util.Arrays.fill( row, 7.0 );
		}
		Delta delta = new Delta( );
		delta.setRegressionWindow( 2 );
		double[][] d = delta.performDelta2D( data );
		for ( int t = 2; t < 8; t++ ) {
			for ( int c = 0; c < 3; c++ ) {
				assertEquals( 0.0, d[ t ][ c ], 1e-12 );
			}
		}
	}

	@Test
	void delta2DOfRampIsProportionalToSlope( ) {
		Delta delta = new Delta( );
		delta.setRegressionWindow( 2 );
		double[][] d = delta.performDelta2D( ramp2D( 10, 3 ) );
		// slope of coefficient c is (c+1); interior values must keep that ratio
		for ( int t = 2; t < 8; t++ ) {
			assertEquals( 2.0, d[ t ][ 1 ] / d[ t ][ 0 ], 1e-9 );
			assertEquals( 3.0, d[ t ][ 2 ] / d[ t ][ 0 ], 1e-9 );
		}
	}

	@Test
	void delta2DPadsWhenFewerFramesThanWindow( ) {
		Delta delta = new Delta( );
		delta.setRegressionWindow( 4 );
		double[][] d = delta.performDelta2D( ramp2D( 2, 3 ) );
		assertEquals( 4, d.length, "output is padded to the regression window length" );
	}

	@Test
	void delta2DOfUnitRampIsOne( ) {
		Delta delta = new Delta( );
		delta.setRegressionWindow( 2 );
		double[][] d = delta.performDelta2D( ramp2D( 10, 1 ) );
		assertEquals( 1.0, d[ 5 ][ 0 ], 1e-9 );
	}

	@Test
	void delta1DMatchesDelta2DInInterior( ) {
		double[] data = { 1, 4, 2, 8, 5, 7, 3, 9, 6, 0 };
		double[][] data2D = new double[ data.length ][ 1 ];
		for ( int i = 0; i < data.length; i++ ) {
			data2D[ i ][ 0 ] = data[ i ];
		}
		Delta delta = new Delta( );
		delta.setRegressionWindow( 1 );
		double[] d1 = delta.performDelta1D( data );
		double[][] d2 = delta.performDelta2D( data2D );
		for ( int t = 1; t < data.length - 1; t++ ) {
			assertEquals( d2[ t ][ 0 ], d1[ t ], 1e-12 );
		}
	}

	@Test
	void delta1DHandlesTrailingBoundaryLikeDelta2D( ) {
		double[] data = { 1, 4, 2, 8, 5, 7, 3, 9, 6, 11 };
		Delta delta = new Delta( );
		delta.setRegressionWindow( 1 );
		double[] d1 = delta.performDelta1D( data );
		assertEquals( 11, d1[ 9 ], 1e-12 );
	}
}
