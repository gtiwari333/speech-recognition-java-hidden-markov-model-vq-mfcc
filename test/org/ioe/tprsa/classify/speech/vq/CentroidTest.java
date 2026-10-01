package org.ioe.tprsa.classify.speech.vq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CentroidTest {

	@Test
	void updateMovesToMeanAndResetsCell( ) {
		Centroid c = new Centroid( new double[] { 0, 0 } );
		c.add( new Points( new double[] { 1, 2 } ), 1.0 );
		c.add( new Points( new double[] { 3, 6 } ), 2.0 );
		assertEquals( 2, c.getNumPts( ) );
		assertEquals( 3.0, c.getDistortion( ), 1e-12 );

		c.update( );

		assertArrayEquals( new double[] { 2, 4 }, c.getAllCo( ), 1e-12 );
		assertEquals( 0, c.getNumPts( ) );
		assertEquals( 0.0, c.getDistortion( ) );
	}

	@Test
	void removeFindsPointByValue( ) {
		Centroid c = new Centroid( new double[] { 0 } );
		c.add( new Points( new double[] { 1 } ), 1.0 );
		c.add( new Points( new double[] { 2 } ), 2.0 );
		c.remove( new Points( new double[] { 1 } ), 1.0 );
		assertEquals( 1, c.getNumPts( ) );
		assertEquals( 2.0, c.getPoint( 0 ).getCo( 0 ) );
		assertEquals( 2.0, c.getDistortion( ), 1e-12 );
	}
}
