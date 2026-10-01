package org.ioe.tprsa.ui.viz;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.XYPlot;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

class ChartsTest {

	@Test
	void lineAndStepCharts( ) {
		JFreeChart c = Charts.line( "t", "x", "y", Charts.Series.of( "a", new double[] { 1, 3, 2 } ), Charts.Series.of( "b", new double[] { 0, 1, 0 } ) );
		assertEquals( 2, c.getXYPlot( ).getDataset( ).getSeriesCount( ) );
		Charts.addFrameCursor( c, 1 );
		Charts.addInterval( c, 0, 1, Charts.VOICED );
		Charts.addSecondary( c, "weight", Charts.Series.of( "w", new double[] { 0, 1, 0 } ) );
		assertEquals( 2, c.getXYPlot( ).getDatasetCount( ) );
		assertEquals( 1, ViewTestSupport.renderAll( Charts.panel( c ) ) );
		assertEquals( 1, ViewTestSupport.renderAll( Charts.panel( Charts.stepLine( "s", "x", "y", Charts.Series.of( "q", new double[] { 0, 0, 1 } ) ) ) ) );
	}

	@Test
	void barHighlightsAndMarks( ) {
		JFreeChart c = Charts.bar( "b", "word", "score", new String[] { "a", "b", "c" }, new double[] { -1, -2, -3 }, 0, 2 );
		CategoryPlot plot = c.getCategoryPlot( );
		assertEquals( 3, plot.getDataset( ).getColumnCount( ) );
		assertEquals( Charts.HIGHLIGHT, plot.getRenderer( ).getItemPaint( 0, 0 ) );
		assertEquals( Charts.MARK, plot.getRenderer( ).getItemPaint( 0, 2 ) );
		assertEquals( Charts.BAR, plot.getRenderer( ).getItemPaint( 0, 1 ) );
		Charts.panel( c ).getChart( ).createBufferedImage( 300, 200 );
	}

	@Test
	void heatMapSkipsNaNAndClampsMinusInfinity( ) {
		double[][] z = { { 1, Double.NaN }, { Double.NEGATIVE_INFINITY, 4 } };
		JFreeChart c = Charts.heatMap( "h", "x", "y", z );
		XYPlot plot = c.getXYPlot( );
		assertEquals( 3, plot.getDataset( ).getItemCount( 0 ), "the NaN cell is left empty" );
		Charts.annotateCells( c, z, "%.1f" );
		c.createBufferedImage( 300, 200 );
		Charts.heatMap( "constant", "x", "y", new double[][] { { 2, 2 } } ).createBufferedImage( 300, 200 );
	}

	@Test
	void helpers( ) {
		assertArrayEquals( new double[][] { { 1, 3 }, { 2, 4 } }, Charts.transpose( new double[][] { { 1, 2 }, { 3, 4 } } ) );
		float[] y = new float[ 10000 ];
		y[ 5000 ] = 7;
		Charts.Series s = Charts.envelope( "e", y, 0.5, 100 );
		assertTrue( s.y( ).length <= 200 );
		assertEquals( 7, java.util.Arrays.stream( s.y( ) ).max( ).orElseThrow( ), "the peak survives down-sampling" );
		assertEquals( 2.0, Charts.envelope( "e", new float[] { 1, 2, 3 }, 1, 100 ).x( )[ 2 ] );
		assertEquals( 2, new ViewState( 7, null ).frameIn( 3 ) );
		assertEquals( 0, new ViewState( -1, null ).frameIn( 3 ) );
		JComponent table = Charts.table( new String[] { "a" }, new Object[][] { { 1 } } );
		assertNotNull( table );
		assertNotNull( Charts.message( "hello" ) );
		assertEquals( 2, ViewTestSupport.renderAll( Charts.stack( Charts.panel( Charts.line( "a", "x", "y", Charts.Series.of( "a", new double[] { 1 } ) ) ) ,
				Charts.panel( Charts.xyBars( "b", "x", "y", new double[] { 1, 2 } ) ) ) ) );
		assertNotEquals( Color.WHITE, Charts.NOISE );
	}
}
