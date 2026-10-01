package org.ioe.tprsa.trace;

import java.util.List;
import java.util.Optional;

/**
 * everything computed while recognising (or verifying) one recording
 *
 * @param source
 *            file name, or "recording" for captured audio
 * @param scores
 *            every word model's score, best first
 * @param expectedWord
 *            the word being verified, or null for plain recognition
 */
public record RecognitionTrace( String source, PreprocessTrace preprocess, FeatureTrace features, VqTrace vq, List< WordScore > scores,
		String recognizedWord, String expectedWord ) {

	public RecognitionTrace {
		scores = List.copyOf( scores );
	}

	public boolean isVerification( ) {
		return expectedWord != null;
	}

	/** verification succeeded: the recognised word is the expected one (ignoring case) */
	public boolean verified( ) {
		return expectedWord != null && expectedWord.equalsIgnoreCase( recognizedWord );
	}

	/** score difference between the best and the second best word */
	public double margin( ) {
		return scores.size( ) < 2 ? Double.POSITIVE_INFINITY : scores.get( 0 ).score( ) - scores.get( 1 ).score( );
	}

	public Optional< WordScore > scoreOf( String word ) {
		return scores.stream( ).filter( s -> s.word( ).equalsIgnoreCase( word ) ).findFirst( );
	}

	/** 1-based rank of the word, 0 when it has no model */
	public int rankOf( String word ) {
		for ( int i = 0; i < scores.size( ); i++ ) {
			if ( scores.get( i ).word( ).equalsIgnoreCase( word ) ) {
				return i + 1;
			}
		}
		return 0;
	}
}
