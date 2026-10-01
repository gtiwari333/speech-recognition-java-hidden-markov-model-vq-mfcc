package org.ioe.tprsa.ui.viz.training;

import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.trace.WordTrainingTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;

import javax.swing.JComponent;

/** training step 5: one row per word */
public final class SummaryStep implements StepView< TrainingSession > {

	@Override
	public String title( ) {
		return "5. Summary";
	}

	@Override
	public String explanation( TrainingSession session, ViewState state ) {
		long skipped = session.words( ).stream( ).filter( WordTrainingTrace::isSkipped ).count( );
		return session.words( ).isEmpty( ) ? "Run Train HMM to see the summary."
				: String.format( "%d words trained, %d skipped.", session.words( ).size( ) - skipped, skipped );
	}

	@Override
	public JComponent build( TrainingSession session, ViewState state ) {
		if ( session.words( ).isEmpty( ) ) {
			return Charts.message( "Run Train HMM to see this step." );
		}
		Object[][] rows = session.words( ).stream( ).map( w -> new Object[] { w.word( ), w.files( ).size( ), w.iterations( ),
				w.isSkipped( ) ? "" : String.format( "%.1f", w.finalLogLikelihood( ) ), w.isSkipped( ) ? "" : w.converged( ) ? "yes" : "no",
				w.isSkipped( ) ? w.skippedReason( ) : "" } ).toArray( Object[][]::new );
		return Charts.table( new String[] { "word", "recordings", "iterations", "final log likelihood", "converged", "skipped" }, rows );
	}
}
