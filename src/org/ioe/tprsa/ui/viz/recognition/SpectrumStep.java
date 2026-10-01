package org.ioe.tprsa.ui.viz.recognition;

import org.ioe.tprsa.trace.FeatureTrace;
import org.ioe.tprsa.trace.PreprocessTrace;
import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.ui.viz.Charts;
import org.ioe.tprsa.ui.viz.StepView;
import org.ioe.tprsa.ui.viz.ViewState;
import org.jfree.chart.JFreeChart;

import javax.swing.JComponent;

/** step 3: magnitude spectrum of the selected frame with the mel filter bank, and its log filter bank energies */
public final class SpectrumStep implements StepView< RecognitionTrace > {

	@Override
	public String title( ) {
		return "3. Spectrum & mel filter bank";
	}

	@Override
	public boolean usesFrame( ) {
		return true;
	}

	@Override
	public String explanation( RecognitionTrace trace, ViewState state ) {
		PreprocessTrace p = trace.preprocess( );
		int f = state.frameIn( trace.features( ).frameCount( ) );
		return String.format( "Frame %d: the %d-point FFT gives the magnitude spectrum |X(k)| up to %d Hz. 30 triangular filters, equally spaced on the "
				+ "mel scale mel(f) = 2595·log10(1 + f/700) between 80 Hz and %d Hz (red, right axis), sum the spectrum into 30 band energies, "
				+ "which are then log compressed (bottom).", f + 1, p.frameSize( ), p.sampleRate( ) / 2, p.sampleRate( ) / 2 );
	}

	@Override
	public JComponent build( RecognitionTrace trace, ViewState state ) {
		FeatureTrace ft = trace.features( );
		PreprocessTrace p = trace.preprocess( );
		int f = state.frameIn( ft.frameCount( ) );
		double hzPerBin = p.sampleRate( ) / ( double ) p.frameSize( );
		double[] magnitude = ft.magnitudeSpectra( )[ f ];
		double[] hz = new double[ magnitude.length ], db = new double[ magnitude.length ];
		for ( int k = 0; k < magnitude.length; k++ ) {
			hz[ k ] = k * hzPerBin;
			db[ k ] = 20 * Math.log10( Math.max( magnitude[ k ], 1e-12 ) );
		}
		JFreeChart spectrum = Charts.line( "Magnitude spectrum, frame " + ( f + 1 ), "frequency (Hz)", "|X(k)| (dB)", new Charts.Series( "spectrum", hz, db ) );
		int[] bins = ft.melCentreBins( );
		Charts.Series[] filters = new Charts.Series[ bins.length - 2 ];
		for ( int k = 1; k + 1 < bins.length; k++ ) {
			filters[ k - 1 ] = new Charts.Series( "filter " + k, new double[] { bins[ k - 1 ] * hzPerBin, bins[ k ] * hzPerBin, bins[ k + 1 ] * hzPerBin },
					new double[] { 0, 1, 0 } );
		}
		Charts.addSecondary( spectrum, "filter weight", filters );
		String[] labels = new String[ ft.logMelEnergies( )[ f ].length ];
		for ( int i = 0; i < labels.length; i++ ) {
			labels[ i ] = String.valueOf( i + 1 );
		}
		JFreeChart energies = Charts.bar( "Log mel filter bank energies, frame " + ( f + 1 ), "filter", "log energy", labels, ft.logMelEnergies( )[ f ], -1, -1 );
		return Charts.stack( Charts.panel( spectrum ), Charts.panel( energies ) );
	}
}
