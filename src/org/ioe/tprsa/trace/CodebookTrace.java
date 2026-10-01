package org.ioe.tprsa.trace;

import java.util.Arrays;
import java.util.List;

/**
 * how the LBG algorithm built the codebook
 *
 * @param splits
 *            one entry per codebook size reached by splitting (2, 4 … 256)
 * @param vectorsPerCodeword
 *            number of training vectors quantised to each codeword
 */
public record CodebookTrace( int trainingVectors, List< SplitTrace > splits, int[] vectorsPerCodeword ) {

	public CodebookTrace {
		splits = List.copyOf( splits );
	}

	/**
	 * @param distortions
	 *            total distortion right after the split, then after each k-means iteration
	 */
	public record SplitTrace( int codebookSize, double[] distortions ) {
	}

	public int unusedCodewords( ) {
		return ( int ) Arrays.stream( vectorsPerCodeword ).filter( n -> n == 0 ).count( );
	}
}
