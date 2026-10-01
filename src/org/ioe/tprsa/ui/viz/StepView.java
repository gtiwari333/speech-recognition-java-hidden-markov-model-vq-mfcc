package org.ioe.tprsa.ui.viz;

import javax.swing.JComponent;

/**
 * one step of the algorithm, rendered from a trace record; never calls the algorithm itself
 */
public interface StepView< T > {

	String title( );

	/** what the step does, with this run's key numbers */
	String explanation( T trace, ViewState state );

	JComponent build( T trace, ViewState state );

	/** whether the frame slider affects this step */
	default boolean usesFrame( ) {
		return false;
	}

	/** whether the word picker affects this step */
	default boolean usesWord( ) {
		return false;
	}
}
