package org.ioe.tprsa.audio.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DCTTest {

	@Test
	void constantInputOnlyHasZerothCoefficient( ) {
		int m = 30;
		double[] y = new double[ m ];
		java.util.Arrays.fill( y, 2.0 );
		double[] c = new DCT( 12, m ).performDCT( y );

		assertEquals( 12, c.length );
		// HTK Book eq. 5.14 includes the sqrt(2/N) factor
		assertEquals( Math.sqrt( 2.0 / m ) * 2.0 * m, c[ 0 ], 1e-9 );
		for ( int n = 1; n < c.length; n++ ) {
			assertEquals( 0.0, c[ n ], 1e-9, "c[" + n + "]" );
		}
	}

	@Test
	void cosineBasisMapsToSingleCoefficient( ) {
		int m = 20, target = 3;
		double[] y = new double[ m ];
		for ( int i = 1; i <= m; i++ ) {
			y[ i - 1 ] = Math.cos( Math.PI * target / m * ( i - 0.5 ) );
		}
		double[] c = new DCT( 8, m ).performDCT( y );
		for ( int n = 0; n < c.length; n++ ) {
			assertEquals( n == target ? Math.sqrt( 2.0 / m ) * m / 2.0 : 0.0, c[ n ], 1e-9, "c[" + n + "]" );
		}
	}
}
