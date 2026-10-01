package org.ioe.tprsa.ui.viz.recognition;

import org.ioe.tprsa.trace.FeatureTrace;
import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;

/** step 4: log mel energies and mean-normalised MFCCs over time */
public final class MfccStep implements StepView< RecognitionTrace > {

	@Override
	public String title( ) {
		return "4. MFCC";
	}

	@Override
	public boolean usesFrame( ) {
		return true;
	}

	@Override
	public String explanation( RecognitionTrace trace, ViewState state ) {
		return String.format( "Each frame's 30 log filter bank energies (top) are decorrelated by a DCT, c_n = √(2/30)·Σ y_i·cos(π·n·(i − 0.5)/30), keeping "
				+ "12 coefficients (c0 included). Subtracting each coefficient's mean over the recording (cepstral mean normalisation) removes a "
				+ "constant microphone / channel effect (bottom). %d frames.", trace.features( ).frameCount( ) );
	}

	@Override
	public JComponent build( RecognitionTrace trace, ViewState state ) {
		FeatureTrace ft = trace.features( );
		int f = state.frameIn( ft.frameCount( ) );
		JFreeChart mel = Charts.heatMap( "Log mel filter bank energies", "frame", "filter", ft.logMelEnergies( ) );
		JFreeChart mfcc = Charts.heatMap( "MFCC after cepstral mean normalisation", "frame", "coefficient", ft.mfcc( ) );
		Charts.addFrameCursor( mel, f );
		Charts.addFrameCursor( mfcc, f );
		return Charts.stack( Charts.panel( mel ), Charts.panel( mfcc ) );
	}
}
