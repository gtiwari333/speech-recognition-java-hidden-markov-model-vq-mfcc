package org.ioe.tprsa.ui.viz;

/**
 * what the user selected in the inspector
 *
 * @param frame
 *            selected frame (0-based) for time based steps
 * @param word
 *            selected word for word based steps, or null
 */
public record ViewState( int frame, String word ) {

	/** the selected frame clamped to 0..count-1 */
	public int frameIn( int count ) {
		return Math.max( 0, Math.min( count - 1, frame ) );
	}
}
