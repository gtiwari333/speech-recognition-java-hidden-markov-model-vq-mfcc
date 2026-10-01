package org.ioe.tprsa.trace;

import java.util.List;
import java.util.Optional;

/**
 * the latest codebook generation and HMM training of this session; either may be missing
 */
public record TrainingSession( CodebookTrace codebook, List< WordTrainingTrace > words ) {

	public static final TrainingSession EMPTY = new TrainingSession( null, List.of( ) );

	public TrainingSession {
		words = List.copyOf( words );
	}

	public TrainingSession withCodebook( CodebookTrace newCodebook ) {
		return new TrainingSession( newCodebook, words );
	}

	public TrainingSession withWords( List< WordTrainingTrace > newWords ) {
		return new TrainingSession( codebook, newWords );
	}

	public Optional< WordTrainingTrace > word( String name ) {
		return words.stream( ).filter( w -> w.word( ).equalsIgnoreCase( name ) ).findFirst( );
	}

	public List< String > wordNames( ) {
		return words.stream( ).map( WordTrainingTrace::word ).toList( );
	}
}
