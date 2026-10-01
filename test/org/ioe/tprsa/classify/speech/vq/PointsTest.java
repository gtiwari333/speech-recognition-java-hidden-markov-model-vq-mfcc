package org.ioe.tprsa.classify.speech.vq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PointsTest {

	@Test
	void equalsComparesAllCoordinates( ) {
		assertTrue( Points.equals( new Points( new double[] { 1, 2, 3 } ), new Points( new double[] { 1, 2, 3 } ) ) );
		assertFalse( Points.equals( new Points( new double[] { 1, 2, 3 } ), new Points( new double[] { 1, 2, 4 } ) ) );
		assertFalse( Points.equals( new Points( new double[] { 1, 2 } ), new Points( new double[] { 1, 2, 3 } ) ) );
	}

	@Test
	void accessors( ) {
		Points p = new Points( new double[] { 1, 2 } );
		assertEquals( 2, p.getDimension( ) );
		p.setCo( 1, 5 );
		assertEquals( 5, p.getCo( 1 ) );
		p.changeCo( new double[] { 9, 8 } );
		assertArrayEquals( new double[] { 9, 8 }, p.getAllCo( ) );
	}
}
