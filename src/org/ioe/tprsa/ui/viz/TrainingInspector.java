package org.ioe.tprsa.ui.viz;

import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.ui.viz.training.*;

import java.util.List;

/** the codebook and HMM training steps of this session */
public final class TrainingInspector extends Inspector< TrainingSession > {

	public TrainingInspector( ) {
		super( List.of( new CodebookStep( ), new TrainingSequencesStep( ), new ConvergenceStep( ), new ModelStep( ), new SummaryStep( ) ),
				"Run Generate CodeBook and Train HMM to see each training step here." );
	}

	@Override
	protected List< String > words( TrainingSession session ) {
		return session.wordNames( );
	}

	@Override
	protected String defaultWord( TrainingSession session ) {
		return session.wordNames( ).isEmpty( ) ? null : session.wordNames( ).get( 0 );
	}

	@Override
	protected int frameCount( TrainingSession session ) {
		return 0;
	}

	@Override
	protected String header( TrainingSession session ) {
		String codebook = session.codebook( ) == null ? "no codebook generated in this session"
				: "codebook of " + session.codebook( ).vectorsPerCodeword( ).length + " codewords";
		return "<html><b>Training</b>: " + codebook + ", " + session.words( ).size( ) + " word(s)</html>";
	}
}
