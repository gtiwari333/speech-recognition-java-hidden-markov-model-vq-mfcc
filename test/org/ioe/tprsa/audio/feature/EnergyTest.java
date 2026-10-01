package org.ioe.tprsa.audio.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnergyTest {

	@Test
	void computesLogOfSumOfSquaresPerFrame( ) {
		float[][] frames = { { 1, 2, 3, 4 }, { 0.5f, 0.5f, 0.5f, 0.5f } };
		double[] e = new Energy( 4 ).calcEnergy( frames );
		assertEquals( Math.log( 30 ), e[ 0 ], 1e-6 );
		assertEquals( Math.log( 1 ), e[ 1 ], 1e-6 );
	}

	@Test
	void silentFrameIsFloored( ) {
		// -Infinity would propagate into the deltas and the VQ distance
		double[] e = new Energy( 4 ).calcEnergy( new float[][] { { 0, 0, 0, 0 } } );
		assertTrue( Double.isFinite( e[ 0 ] ) );
		assertTrue( e[ 0 ] < Math.log( 1e-6 ) );
	}
}
