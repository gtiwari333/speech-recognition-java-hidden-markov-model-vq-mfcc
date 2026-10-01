package org.ioe.tprsa.ui.viz.training;

import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.trace.WordTrainingTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;
import java.util.Optional;

/** training step 3: Baum-Welch log likelihood per iteration */
public final class ConvergenceStep implements StepView< TrainingSession > {

	@Override
	public String title( ) {
		return "3. Baum-Welch convergence";
	}

	@Override
	public boolean usesWord( ) {
		return true;
	}

	@Override
	public String explanation( TrainingSession session, ViewState state ) {
		return WordSteps.word( session, state ).filter( w -> !w.isSkipped( ) )
				.map( w -> String.format( "Baum-Welch (EM) re-estimates the transition and output probabilities so that the training sequences become more "
						+ "likely; each iteration can only increase the total log likelihood. %s stopped after %d iterations (%s), log likelihood %.1f.",
						w.word( ), w.iterations( ), w.converged( ) ? "converged: change below 1e-5" : "iteration limit reached", w.finalLogLikelihood( ) ) )
				.orElse( "Run Train HMM to see the convergence." );
	}

	@Override
	public JComponent build( TrainingSession session, ViewState state ) {
		Optional< WordTrainingTrace > word = WordSteps.word( session, state );
		JComponent unavailable = WordSteps.unavailable( word );
		if ( unavailable != null ) {
			return unavailable;
		}
		WordTrainingTrace w = word.get( );
		double[] iteration = Charts.indices( w.iterations( ) );
		for ( int i = 0; i < iteration.length; i++ ) {
			iteration[ i ] += 1;
		}
		JFreeChart chart = Charts.line( "Log likelihood of the training sequences, " + w.word( ), "iteration", "log likelihood",
				new Charts.Series( "log likelihood", iteration, w.logLikelihoods( ) ) );
		Charts.addFrameCursor( chart, w.iterations( ) );
		return Charts.panel( chart );
	}
}
