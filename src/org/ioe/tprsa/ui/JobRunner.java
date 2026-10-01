package org.ioe.tprsa.ui;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * runs one job at a time in a SwingWorker; job controls are disabled while it runs, progress and errors go to the status line
 */
public final class JobRunner {

	/** work done off the event dispatch thread */
	public interface Job< T > {
		T run( Consumer< String > progress ) throws Exception;
	}

	private final List< JComponent >	controls	= new ArrayList<>( );
	private final JLabel				status;
	private final JProgressBar			bar;
	private volatile boolean			running;

	public JobRunner( JLabel status, JProgressBar bar ) {
		this.status = status;
		this.bar = bar;
		bar.setVisible( false );
	}

	/** controls that start jobs; disabled while any job runs */
	public void register( JComponent... jobControls ) {
		controls.addAll( Arrays.asList( jobControls ) );
	}

	public boolean isRunning( ) {
		return running;
	}

	/**
	 * starts the job unless another one is running; call on the event dispatch thread
	 *
	 * @return false when another job is still running (nothing is started)
	 */
	public < T > boolean run( String description, Job< T > job, Consumer< T > onSuccess, Consumer< Throwable > onFailure ) {
		if ( running ) {
			return false;
		}
		running = true;
		setControlsEnabled( false );
		status.setText( description + " …" );
		bar.setIndeterminate( true );
		bar.setVisible( true );
		new SwingWorker< T, String >( ) {
			@Override
			protected T doInBackground( ) throws Exception {
				return job.run( this::progress );
			}

			private void progress( String message ) {
				publish( message );
			}

			@Override
			protected void process( List< String > messages ) {
				status.setText( messages.get( messages.size( ) - 1 ) );
			}

			@Override
			protected void done( ) {
				running = false;
				setControlsEnabled( true );
				bar.setVisible( false );
				try {
					T result = get( );
					status.setText( description + ": done" );
					try {
						onSuccess.accept( result );
					} catch ( RuntimeException e ) {
						status.setText( description + " failed: " + message( e ) );
						onFailure.accept( e );
					}
				} catch ( ExecutionException e ) {
					Throwable cause = e.getCause( ) == null ? e : e.getCause( );
					status.setText( description + " failed: " + message( cause ) );
					onFailure.accept( cause );
				} catch ( InterruptedException | CancellationException e ) {
					status.setText( description + " cancelled" );
				}
			}
		}.execute( );
		return true;
	}

	private void setControlsEnabled( boolean enabled ) {
		for ( JComponent c : controls ) {
			c.setEnabled( enabled );
		}
	}

	/** readable message of an exception */
	public static String message( Throwable t ) {
		return t.getMessage( ) == null ? t.getClass( ).getSimpleName( ) : t.getMessage( );
	}
}
