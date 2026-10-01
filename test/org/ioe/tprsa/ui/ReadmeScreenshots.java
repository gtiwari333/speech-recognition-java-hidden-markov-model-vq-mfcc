package org.ioe.tprsa.ui;

import org.ioe.tprsa.TestFiles;
import org.ioe.tprsa.mediator.Operations;
import org.ioe.tprsa.trace.RecognitionTrace;
import org.ioe.tprsa.trace.TrainingSession;
import org.ioe.tprsa.ui.viz.RecognitionInspector;
import org.ioe.tprsa.ui.viz.TrainingInspector;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * regenerates the README screenshots (docs/screenshots/*.png): opens the real main window, feeds its inspectors
 * real traces (recognition with the committed models, training on a temporary copy of TrainWav so models/ is not
 * touched) and paints the window into PNG files. Not a unit test: it needs a display. Run from the project root:
 *
 * <pre>
 * mvn -q test-compile
 * java -cp "target/classes:target/test-classes:$HOME/.m2/repository/org/jfree/jfreechart/1.5.6/jfreechart-1.5.6.jar" \
 *     org.ioe.tprsa.ui.ReadmeScreenshots docs/screenshots
 * </pre>
 */
public final class ReadmeScreenshots {

	private final JFrame	frame;
	private final File		outDir;

	private ReadmeScreenshots( JFrame frame, File outDir ) {
		this.frame = frame;
		this.outDir = outDir;
	}

	public static void main( String[] args ) throws Exception {
		File outDir = new File( args.length > 0 ? args[ 0 ] : "docs/screenshots" );
		outDir.mkdirs( );
		System.setOut( new java.io.PrintStream( java.io.OutputStream.nullOutputStream( ) ) ); // the algorithm classes are chatty

		RecognitionTrace recognised = new Operations( ).recognizeWithTrace( new File( "TrainWav/Developer/Developer2.wav" ), "Developer" );
		RecognitionTrace failedVerification = new Operations( ).recognizeWithTrace( new File( "TrainWav/Zebra/Zebra0.wav" ), "Ship" );
		TrainingSession training = trainOnACopy( );

		HMM_VQ_Speech_Recognition[ ] window = new HMM_VQ_Speech_Recognition[ 1 ];
		SwingUtilities.invokeAndWait( ( ) -> {
			window[ 0 ] = new HMM_VQ_Speech_Recognition( );
			window[ 0 ].setVisible( true );
		} );
		RecognitionInspector recognition = ( RecognitionInspector ) field( window[ 0 ], "recognitionInspector" );
		TrainingInspector trainer = ( TrainingInspector ) field( window[ 0 ], "trainingInspector" );
		JTabbedPane tabs = ( JTabbedPane ) field( window[ 0 ], "inspectorTabs" );
		ReadmeScreenshots shots = new ReadmeScreenshots( window[ 0 ], outDir );

		shots.take( "recognition-1-waveform", ( ) -> {
			recognition.show( recognised );
			recognition.selectStep( 0 );
		} );
		shots.take( "recognition-3-spectrum", ( ) -> {
			recognition.selectStep( 2 );
			recognition.setFrame( 40 );
		} );
		shots.take( "recognition-4-mfcc", ( ) -> recognition.selectStep( 3 ) );
		shots.take( "recognition-7-scores", ( ) -> {
			recognition.show( failedVerification );
			recognition.selectStep( 6 );
		} );
		shots.take( "recognition-8-best-path", ( ) -> {
			recognition.selectStep( 7 );
			recognition.selectWord( "Ship" );
		} );
		shots.take( "training-1-codebook", ( ) -> {
			trainer.show( training );
			tabs.setSelectedComponent( trainer );
			trainer.selectStep( 0 );
		} );
		shots.take( "training-3-convergence", ( ) -> {
			trainer.selectStep( 2 );
			trainer.selectWord( "Hello" );
		} );
		shots.take( "training-4-model", ( ) -> trainer.selectStep( 3 ) );
		System.exit( 0 );
	}

	/** codebook + HMM training on a temporary copy of TrainWav, so the project's models/ stay untouched */
	private static TrainingSession trainOnACopy( ) throws Exception {
		Path dir = Files.createTempDirectory( "readme-screenshots" );
		TestFiles.copyTree( Paths.get( "TrainWav" ), dir.resolve( "TrainWav" ) );
		Operations op = new Operations( dir );
		return TrainingSession.EMPTY.withCodebook( op.generateCodebookWithTrace( m -> { } ) ).withWords( op.hmmTrainWithTrace( m -> { } ) );
	}

	/** the inspectors are private to the window; reflection keeps this tool out of the production code */
	private static Object field( Object owner, String name ) throws Exception {
		Field f = owner.getClass( ).getDeclaredField( name );
		f.setAccessible( true );
		return f.get( owner );
	}

	/** runs the UI action on the event dispatch thread, waits for the repaint, and paints the window into a PNG */
	private void take( String name, Runnable action ) throws Exception {
		SwingUtilities.invokeAndWait( action );
		Thread.sleep( 500 );
		BufferedImage image = new BufferedImage( frame.getWidth( ), frame.getHeight( ), BufferedImage.TYPE_INT_RGB );
		SwingUtilities.invokeAndWait( ( ) -> frame.getContentPane( ).paint( image.getGraphics( ) ) );
		ImageIO.write( image, "png", new File( outDir, name + ".png" ) );
	}
}
