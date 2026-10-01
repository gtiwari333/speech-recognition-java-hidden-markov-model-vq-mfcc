package org.ioe.tprsa.ui.viz;

import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.trace.WordScore;
import org.ioe.tprsa.ui.viz.recognition.*;

import java.util.List;

/** the 8 recognition / verification steps of the most recent recording */
public final class RecognitionInspector extends Inspector< RecognitionTrace > {

	public RecognitionInspector( ) {
		super( List.of( new WaveformStep( ), new FramingStep( ), new SpectrumStep( ), new MfccStep( ), new DeltaEnergyStep( ), new VqStep( ),
				new ScoresStep( ), new BestPathStep( ) ), "Verify or recognize a word to see each step of the algorithm here." );
	}

	@Override
	protected List< String > words( RecognitionTrace trace ) {
		return trace.scores( ).stream( ).map( WordScore::word ).toList( );
	}

	@Override
	protected String defaultWord( RecognitionTrace trace ) {
		return trace.recognizedWord( );
	}

	@Override
	protected int frameCount( RecognitionTrace trace ) {
		return trace.features( ).frameCount( );
	}

	@Override
	protected String header( RecognitionTrace trace ) {
		String verification = !trace.isVerification( ) ? ""
				: trace.verified( ) ? " &nbsp;(expected " + trace.expectedWord( ) + " ✔)" : " &nbsp;(expected " + trace.expectedWord( ) + " ✘)";
		return "<html><b>" + trace.recognizedWord( ) + "</b> recognized from " + trace.source( ) + verification + "</html>";
	}
}
