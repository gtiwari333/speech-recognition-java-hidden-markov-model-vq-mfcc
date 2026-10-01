package org.ioe.tprsa.trace;

import java.util.Arrays;

/**
 * vector quantization of one recording
 *
 * @param codewords
 *            index of the nearest codeword per frame
 * @param distances
 *            Euclidean distance of each frame's feature vector to that codeword
 */
public record VqTrace( int codebookSize, int[] codewords, double[] distances ) {

	public double meanDistance( ) {
		return Arrays.stream( distances ).average( ).orElse( 0 );
	}

	public int distinctCodewords( ) {
		return ( int ) Arrays.stream( codewords ).distinct( ).count( );
	}
}
