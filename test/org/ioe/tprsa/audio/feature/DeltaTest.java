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
	void delta2DOfConstantIsZero( ) {
		double[][] data = new double[ 10 ][ 3 ];
		for ( double[] row : data ) {
			java.util.Arrays.fill( row, 7.0 );
		}
		Delta delta = new Delta( );
		delta.setRegressionWindow( 2 );
		double[][] d = delta.performDelta2D( data );
		for ( int t = 0; t < 10; t++ ) {
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
	void sequenceShorterThanTheWindowKeepsItsLength( ) {
		Delta delta = new Delta( );
		delta.setRegressionWindow( 4 );
		double[][] d = delta.performDelta2D( ramp2D( 2, 3 ) );
		assertEquals( 2, d.length );
		// frame 1 is replicated for t+1..t+4: (1+2+3+4) * (c1 - c0) / (2 * 30), c1 - c0 = slope (c + 1)
		assertEquals( 10.0 / 60, d[ 0 ][ 0 ], 1e-12 );
		assertEquals( 30.0 / 60, d[ 0 ][ 2 ], 1e-12 );
	}

	@Test
	void boundariesReplicateFirstAndLastFrameLikeHtk( ) {
		// HTK Book eq. 5.16, DELTAWINDOW = 2, values checked against an independent implementation
		double[] x = { 3, 1, 4, 1, 5, 9, 2, 6, 5 };
		double[][] data = new double[ x.length ][ 1 ];
		for ( int i = 0; i < x.length; i++ ) {
			data[ i ][ 0 ] = x[ i ];
		}
		Delta delta = new Delta( );
		delta.setRegressionWindow( 2 );
		double[][] d = delta.performDelta2D( data );
		double[] expected = { 0.0, -0.3, 0.4, 1.7, 0.4, 0.7, -0.3, -0.5, 0.5 };
		for ( int t = 0; t < x.length; t++ ) {
			assertEquals( expected[ t ], d[ t ][ 0 ], 1e-12, "t = " + t );
		}
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
	void delta1DReplicatesTheBoundaryFrames( ) {
		double[] data = { 1, 4, 2, 8, 5, 7, 3, 9, 6, 11 };
		Delta delta = new Delta( );
		delta.setRegressionWindow( 1 );
		double[] d1 = delta.performDelta1D( data );
		assertEquals( ( 4 - 1 ) / 2.0, d1[ 0 ], 1e-12 );
		assertEquals( ( 11 - 6 ) / 2.0, d1[ 9 ], 1e-12 );
	}
}
