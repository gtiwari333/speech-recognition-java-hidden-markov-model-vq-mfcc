package org.ioe.tprsa.ui.viz.training;

import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.trace.WordTrainingTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.ViewState;

import javax.swing.JComponent;
import java.util.Optional;

/** shared helpers of the word based training steps */
final class WordSteps {

	private WordSteps( ) {
	}

	/** the selected word, or the first one */
	static Optional< WordTrainingTrace > word( TrainingSession session, ViewState state ) {
		if ( session.words( ).isEmpty( ) ) {
			return Optional.empty( );
		}
		return Optional.of( state.word( ) == null ? session.words( ).get( 0 ) : session.word( state.word( ) ).orElse( session.words( ).get( 0 ) ) );
	}

	/** message for a missing or skipped word, or null when the word can be shown */
	static JComponent unavailable( Optional< WordTrainingTrace > word ) {
		if ( word.isEmpty( ) ) {
			return Charts.message( "Run Train HMM to see this step." );
		}
		if ( word.get( ).isSkipped( ) ) {
			return Charts.message( word.get( ).word( ) + " was skipped: " + word.get( ).skippedReason( ) );
		}
		return null;
	}
}
