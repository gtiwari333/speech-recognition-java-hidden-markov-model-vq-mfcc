package org.ioe.tprsa.classify.speech.vq;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CodebookTest {

	/** n points scattered around each centre */
	private static Points[] clusters( double[][] centres, int n, double spread, long seed ) {
		Random r = new Random( seed );
		Points[] pts = new Points[ centres.length * n ];
		for ( int c = 0; c < centres.length; c++ ) {
			for ( int i = 0; i < n; i++ ) {
				double[] co = new double[ centres[ c ].length ];
				for ( int d = 0; d < co.length; d++ ) {
					co[ d ] = centres[ c ][ d ] + spread * r.nextGaussian( );
				}
				pts[ c * n + i ] = new Points( co );
			}
		}
		return pts;
	}

	@Test
	void separatesTwoClusters( ) {
		Points[] pts = clusters( new double[][] { { 1, 1 }, { 10, 10 } }, 50, 0.3, 1 );
		Codebook cb = new Codebook( pts, 2 );
		int[] q = cb.quantize( pts );

		for ( int i = 1; i < 50; i++ ) {
			assertEquals( q[ 0 ], q[ i ] );
			assertEquals( q[ 50 ], q[ 50 + i ] );
		}
		assertNotEquals( q[ 0 ], q[ 50 ] );
	}

	@Test
	void producesRequestedNumberOfNonEmptyCentroids( ) {
		Points[] pts = clusters( new double[][] { { 1, 2 }, { 4, 9 }, { 8, 3 }, { 12, 12 } }, 40, 0.5, 2 );
		Codebook cb = new Codebook( pts, 8 );
		assertEquals( 8, cb.centroids.length );
		for ( Centroid c : cb.centroids ) {
			for ( double v : c.getAllCo( ) ) {
				assertTrue( Double.isFinite( v ) );
			}
		}
		int[] q = cb.quantize( pts );
		for ( int idx : q ) {
			assertTrue( idx >= 0 && idx < 8 );
		}
	}

	@Test
	void trainedCodebookHasLowerDistortionThanSingleCentroid( ) {
		Points[] pts = clusters( new double[][] { { 1, 2 }, { 4, 9 }, { 8, 3 }, { 12, 12 } }, 40, 0.5, 3 );
		double one = new Codebook( pts, 1 ).getDistortion( pts );
		double four = new Codebook( pts, 4 ).getDistortion( pts );
		assertTrue( four < one / 3, "1 centroid: " + one + ", 4 centroids: " + four );
	}

	@Test
	void centroidsAreAtTheMeanOfTheirCells( ) {
		// k-means fixed point: after training every codeword is (close to) the mean of the points quantised to it
		Points[] pts = clusters( new double[][] { { 1, 2 }, { 4, 9 }, { 8, 3 }, { 12, 12 } }, 40, 1.5, 4 );
		Codebook cb = new Codebook( pts, 4 );
		int[] q = cb.quantize( pts );
		for ( int c = 0; c < cb.centroids.length; c++ ) {
			double[] sum = new double[ 2 ];
			int n = 0;
			for ( int i = 0; i < pts.length; i++ ) {
				if ( q[ i ] == c ) {
					sum[ 0 ] += pts[ i ].getCo( 0 );
					sum[ 1 ] += pts[ i ].getCo( 1 );
					n++;
				}
			}
			assertTrue( n > 0 );
			assertEquals( sum[ 0 ] / n, cb.centroids[ c ].getCo( 0 ), 0.1, "centroid " + c );
			assertEquals( sum[ 1 ] / n, cb.centroids[ c ].getCo( 1 ), 0.1, "centroid " + c );
		}
	}

	@Test
	void notEnoughPointsLeavesCodebookUntrained( ) {
		Codebook cb = new Codebook( new Points[] { new Points( new double[] { 1 } ) }, 4 );
		assertNull( cb.centroids, "constructor only prints an error; callers get an NPE later" );
	}

	@Test
	void quantizeWithTraceRecordsCodewordsAndDistances( ) {
		Points[] pts = clusters( new double[][] { { 1, 2 }, { 4, 9 }, { 8, 3 }, { 12, 12 } }, 20, 0.5, 6 );
		Codebook cb = new Codebook( pts, 4 );
		org.ioe.tprsa.trace.VqTrace t = cb.quantizeWithTrace( pts );
		assertEquals( 4, t.codebookSize( ) );
		assertEquals( 4, cb.size( ) );
		assertArrayEquals( cb.quantize( pts ), t.codewords( ) );
		double sum = 0;
		for ( int i = 0; i < pts.length; i++ ) {
			double[] c = cb.centroids[ t.codewords( )[ i ] ].getAllCo( );
			double d = Math.hypot( pts[ i ].getCo( 0 ) - c[ 0 ], pts[ i ].getCo( 1 ) - c[ 1 ] );
			assertEquals( d, t.distances( )[ i ], 1e-12 );
			sum += d;
		}
		assertEquals( sum / pts.length, t.meanDistance( ), 1e-12 );
		assertEquals( 4, t.distinctCodewords( ) );
	}
}
