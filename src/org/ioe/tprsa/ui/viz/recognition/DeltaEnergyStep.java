package org.ioe.tprsa.ui.viz.recognition;

import org.ioe.tprsa.trace.FeatureTrace;
import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;

/** step 5: delta and delta-delta MFCCs, log energy and its deltas */
public final class DeltaEnergyStep implements StepView< RecognitionTrace > {

	@Override
	public String title( ) {
		return "5. Deltas & energy";
	}

	@Override
	public boolean usesFrame( ) {
		return true;
	}

	@Override
	public String explanation( RecognitionTrace trace, ViewState state ) {
		return "Deltas describe how the coefficients change over time: d_t = Σ_{m=1..M} m·(c_{t+m} − c_{t−m}) / (2·Σ m²), with M = 2 for ΔMFCC and "
				+ "M = 1 for ΔΔMFCC (the delta of the delta); at the start and end the first / last frame is repeated. The log energy log Σ s² of "
				+ "each raw frame and its Δ and ΔΔ complete the 39 values per frame: 12 MFCC + 12 Δ + 12 ΔΔ + 3 energy.";
	}

	@Override
	public JComponent build( RecognitionTrace trace, ViewState state ) {
		FeatureTrace ft = trace.features( );
		int f = state.frameIn( ft.frameCount( ) );
		JFreeChart delta = Charts.heatMap( "ΔMFCC", "frame", "coefficient", ft.deltaMfcc( ) );
		JFreeChart deltaDelta = Charts.heatMap( "ΔΔMFCC", "frame", "coefficient", ft.deltaDeltaMfcc( ) );
		JFreeChart energy = Charts.line( "Log energy", "frame", "value", Charts.Series.of( "log E", ft.logEnergy( ) ),
				Charts.Series.of( "Δ log E", ft.deltaLogEnergy( ) ), Charts.Series.of( "ΔΔ log E", ft.deltaDeltaLogEnergy( ) ) );
		for ( JFreeChart c : new JFreeChart[] { delta, deltaDelta, energy } ) {
			Charts.addFrameCursor( c, f );
		}
		return Charts.stack( Charts.panel( delta ), Charts.panel( deltaDelta ), Charts.panel( energy ) );
	}
}
