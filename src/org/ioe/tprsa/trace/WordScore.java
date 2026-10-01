package org.ioe.tprsa.trace;

/**
 * how one word's HMM scored a recording
 *
 * @param score
 *            Viterbi log probability of the best state path
 * @param statePath
 *            best state per frame
 * @param viterbiGrid
 *            best log score of any path ending in each state, [frame][state]
 */
public record WordScore( String word, double score, int[] statePath, double[][] viterbiGrid ) {
}
