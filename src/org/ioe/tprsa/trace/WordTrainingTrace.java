package org.ioe.tprsa.trace;

import java.util.List;

/**
 * how one word's HMM was trained
 *
 * @param logLikelihoods
 *            total log likelihood of the training sequences under the model before each Baum-Welch re-estimation
 * @param converged
 *            stopped by the convergence threshold rather than the iteration limit
 * @param skippedReason
 *            why the word was not trained, or null
 */
public record WordTrainingTrace( String word, List< String > files, List< int[] > codewordSequences, double[] logLikelihoods,
		boolean converged, double[][] initialTransition, double[][] initialOutput, double[][] finalTransition, double[][] finalOutput,
		String skippedReason ) {

	public WordTrainingTrace {
		files = List.copyOf( files );
		codewordSequences = List.copyOf( codewordSequences );
	}

	public static WordTrainingTrace skipped( String word, String reason ) {
		return new WordTrainingTrace( word, List.of( ), List.of( ), new double[ 0 ], false, null, null, null, null, reason );
	}

	public boolean isSkipped( ) {
		return skippedReason != null;
	}

	public int iterations( ) {
		return logLikelihoods.length;
	}

	public double finalLogLikelihood( ) {
		return logLikelihoods.length == 0 ? Double.NaN : logLikelihoods[ logLikelihoods.length - 1 ];
	}
}
