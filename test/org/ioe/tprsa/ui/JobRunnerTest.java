package org.ioe.tprsa.ui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JobRunnerTest {

	private static boolean onEdt( java.util.function.BooleanSupplier check ) throws Exception {
		boolean[] result = new boolean[ 1 ];
		SwingUtilities.invokeAndWait( ( ) -> result[ 0 ] = check.getAsBoolean( ) );
		return result[ 0 ];
	}

	@Test
	void disablesJobControlsWhileRunningAndDeliversTheResult( ) throws Exception {
		JButton button = new JButton( );
		JLabel status = new JLabel( );
		JobRunner jobs = new JobRunner( status, new JProgressBar( ) );
		jobs.register( button );
		CountDownLatch release = new CountDownLatch( 1 ), finished = new CountDownLatch( 1 );
		AtomicReference< String > result = new AtomicReference<>( );

		SwingUtilities.invokeAndWait( ( ) -> assertTrue( jobs.run( "Job", progress -> {
			progress.accept( "halfway" );
			release.await( 5, TimeUnit.SECONDS );
			return "done!";
		}, r -> {
			result.set( r );
			finished.countDown( );
		}, t -> fail( t ) ) ) );

		assertTrue( jobs.isRunning( ) );
		assertFalse( onEdt( button::isEnabled ) );
		release.countDown( );
		assertTrue( finished.await( 5, TimeUnit.SECONDS ) );
		assertEquals( "done!", result.get( ) );
		assertTrue( onEdt( button::isEnabled ) );
		assertFalse( jobs.isRunning( ) );
	}

	@Test
	void secondJobIsIgnoredWhileOneRuns( ) throws Exception {
		JobRunner jobs = new JobRunner( new JLabel( ), new JProgressBar( ) );
		CountDownLatch release = new CountDownLatch( 1 ), finished = new CountDownLatch( 1 );
		boolean[] started = new boolean[ 2 ];
		SwingUtilities.invokeAndWait( ( ) -> {
			started[ 0 ] = jobs.run( "first", p -> release.await( 5, TimeUnit.SECONDS ), r -> finished.countDown( ), t -> { } );
			started[ 1 ] = jobs.run( "second", p -> "x", r -> fail( "must not run" ), t -> { } );
		} );
		assertTrue( started[ 0 ] );
		assertFalse( started[ 1 ] );
		release.countDown( );
		assertTrue( finished.await( 5, TimeUnit.SECONDS ) );
	}

	@Test
	void failuresAreReportedWithTheirMessage( ) throws Exception {
		JLabel status = new JLabel( );
		JobRunner jobs = new JobRunner( status, new JProgressBar( ) );
		CountDownLatch failed = new CountDownLatch( 1 );
		AtomicReference< Throwable > error = new AtomicReference<>( );
		SwingUtilities.invokeAndWait( ( ) -> jobs.run( "Recognizing", p -> {
			throw new IllegalStateException( "Train first: no codebook found" );
		}, r -> fail( "must fail" ), t -> {
			error.set( t );
			failed.countDown( );
		} ) );
		assertTrue( failed.await( 5, TimeUnit.SECONDS ) );
		assertInstanceOf( IllegalStateException.class, error.get( ) );
		assertTrue( onEdt( ( ) -> status.getText( ).contains( "Train first: no codebook found" ) ) );
		assertEquals( "NullPointerException", JobRunner.message( new NullPointerException( ) ) );
	}
}
