package org.ioe.tprsa.ui.viz;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardXYZToolTipGenerator;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYStepRenderer;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.DefaultXYZDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * small factory for the charts of the step views (JFreeChart); every chart panel has tooltips, zoom and "Save as PNG"
 */
public final class Charts {

	public static final Color	NOISE		= new Color( 120, 120, 120, 60 );
	public static final Color	VOICED		= new Color( 40, 170, 80, 50 );
	public static final Color	SELECTED	= new Color( 230, 140, 20, 70 );
	public static final Color	HIGHLIGHT	= new Color( 30, 110, 200 );
	public static final Color	MARK		= new Color( 230, 120, 20 );
	public static final Color	BAR			= new Color( 150, 160, 175 );

	private Charts( ) {
	}

	/** an x/y data series */
	public record Series( String name, double[] x, double[] y ) {

		/** y against its index 0..n-1 */
		public static Series of( String name, double[] y ) {
			return new Series( name, indices( y.length ), y );
		}
	}

	public static double[] indices( int n ) {
		double[] x = new double[ n ];
		for ( int i = 0; i < n; i++ ) {
			x[ i ] = i;
		}
		return x;
	}

	public static double[] toDouble( float[] values ) {
		double[] d = new double[ values.length ];
		for ( int i = 0; i < values.length; i++ ) {
			d[ i ] = values[ i ];
		}
		return d;
	}

	public static double[][] transpose( double[][] m ) {
		double[][] t = new double[ m[ 0 ].length ][ m.length ];
		for ( int i = 0; i < m.length; i++ ) {
			for ( int j = 0; j < m[ 0 ].length; j++ ) {
				t[ j ][ i ] = m[ i ][ j ];
			}
		}
		return t;
	}

	/**
	 * down-samples a long signal to at most 2 * maxPoints points, keeping each bucket's minimum and maximum
	 *
	 * @param xStep
	 *            x distance between samples (e.g. 1 / sample rate)
	 */
	public static Series envelope( String name, float[] y, double xStep, int maxPoints ) {
		if ( y.length <= 2 * maxPoints ) {
			double[] x = indices( y.length );
			for ( int i = 0; i < x.length; i++ ) {
				x[ i ] *= xStep;
			}
			return new Series( name, x, toDouble( y ) );
		}
		int bucket = ( int ) Math.ceil( y.length / ( double ) maxPoints );
		List< double[] > points = new ArrayList<>( );
		for ( int start = 0; start < y.length; start += bucket ) {
			int end = Math.min( y.length, start + bucket );
			int lo = start, hi = start;
			for ( int i = start; i < end; i++ ) {
				lo = y[ i ] < y[ lo ] ? i : lo;
				hi = y[ i ] > y[ hi ] ? i : hi;
			}
			points.add( new double[] { Math.min( lo, hi ) * xStep, y[ Math.min( lo, hi ) ] } );
			points.add( new double[] { Math.max( lo, hi ) * xStep, y[ Math.max( lo, hi ) ] } );
		}
		return new Series( name, points.stream( ).mapToDouble( p -> p[ 0 ] ).toArray( ), points.stream( ).mapToDouble( p -> p[ 1 ] ).toArray( ) );
	}

	private static XYSeriesCollection collection( Series... series ) {
		XYSeriesCollection c = new XYSeriesCollection( );
		for ( Series s : series ) {
			XYSeries xy = new XYSeries( s.name( ), false, true );
			for ( int i = 0; i < s.y( ).length; i++ ) {
				xy.add( s.x( )[ i ], s.y( )[ i ] );
			}
			c.addSeries( xy );
		}
		return c;
	}

	public static JFreeChart line( String title, String xLabel, String yLabel, Series... series ) {
		JFreeChart chart = ChartFactory.createXYLineChart( title, xLabel, yLabel, collection( series ), PlotOrientation.VERTICAL, series.length > 1, true, false );
		( ( NumberAxis ) chart.getXYPlot( ).getRangeAxis( ) ).setAutoRangeIncludesZero( false );
		return chart;
	}

	public static JFreeChart stepLine( String title, String xLabel, String yLabel, Series... series ) {
		JFreeChart chart = line( title, xLabel, yLabel, series );
		XYStepRenderer renderer = new XYStepRenderer( );
		renderer.setDefaultToolTipGenerator( new org.jfree.chart.labels.StandardXYToolTipGenerator( ) );
		chart.getXYPlot( ).setRenderer( renderer );
		return chart;
	}

	/**
	 * @param highlight
	 *            index of the bar drawn in {@link #HIGHLIGHT}, or -1
	 * @param mark
	 *            index of the bar drawn in {@link #MARK}, or -1
	 */
	public static JFreeChart bar( String title, String categoryLabel, String valueLabel, String[] labels, double[] values, int highlight, int mark ) {
		DefaultCategoryDataset data = new DefaultCategoryDataset( );
		for ( int i = 0; i < labels.length; i++ ) {
			data.addValue( values[ i ], valueLabel, labels[ i ] );
		}
		JFreeChart chart = ChartFactory.createBarChart( title, categoryLabel, valueLabel, data, PlotOrientation.VERTICAL, false, true, false );
		BarRenderer renderer = new BarRenderer( ) {
			@Override
			public Paint getItemPaint( int row, int column ) {
				return column == highlight ? HIGHLIGHT : column == mark ? MARK : BAR;
			}
		};
		renderer.setDefaultToolTipGenerator( new org.jfree.chart.labels.StandardCategoryToolTipGenerator( ) );
		chart.getCategoryPlot( ).setRenderer( renderer );
		return chart;
	}

	/** bars at x = 0..n-1, e.g. a histogram over codewords */
	public static JFreeChart xyBars( String title, String xLabel, String yLabel, double[] values ) {
		return ChartFactory.createXYBarChart( title, xLabel, false, yLabel, collection( Series.of( yLabel, values ) ), PlotOrientation.VERTICAL, false, true, false );
	}

	/**
	 * @param z
	 *            z[x][y]; NaN cells are left empty, -infinity is drawn in the lowest colour
	 */
	public static JFreeChart heatMap( String title, String xLabel, String yLabel, double[][] z ) {
		double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
		for ( double[] column : z ) {
			for ( double v : column ) {
				if ( Double.isFinite( v ) ) {
					min = Math.min( min, v );
					max = Math.max( max, v );
				}
			}
		}
		if ( min == Double.POSITIVE_INFINITY ) {
			min = 0;
			max = 1;
		}
		if ( max <= min ) {
			max = min + 1;
		}
		List< double[] > cells = new ArrayList<>( );
		for ( int x = 0; x < z.length; x++ ) {
			for ( int y = 0; y < z[ x ].length; y++ ) {
				double v = z[ x ][ y ];
				if ( !Double.isNaN( v ) ) {
					cells.add( new double[] { x, y, v == Double.NEGATIVE_INFINITY ? min : v } );
				}
			}
		}
		DefaultXYZDataset data = new DefaultXYZDataset( );
		data.addSeries( title, new double[][] { cells.stream( ).mapToDouble( c -> c[ 0 ] ).toArray( ), cells.stream( ).mapToDouble( c -> c[ 1 ] ).toArray( ),
				cells.stream( ).mapToDouble( c -> c[ 2 ] ).toArray( ) } );
		LookupPaintScale scale = new LookupPaintScale( min, max, Color.WHITE );
		for ( int i = 0; i < 64; i++ ) {
			float f = i / 63f;
			scale.add( min + ( max - min ) * f, new Color( Color.HSBtoRGB( 0.66f - 0.5f * f, 0.85f, 0.35f + 0.6f * f ) ) );
		}
		XYBlockRenderer renderer = new XYBlockRenderer( );
		renderer.setPaintScale( scale );
		renderer.setDefaultToolTipGenerator( new StandardXYZToolTipGenerator( ) );
		NumberAxis xAxis = new NumberAxis( xLabel );
		NumberAxis yAxis = new NumberAxis( yLabel );
		xAxis.setAutoRangeIncludesZero( false );
		yAxis.setAutoRangeIncludesZero( false );
		XYPlot plot = new XYPlot( data, xAxis, yAxis, renderer );
		JFreeChart chart = new JFreeChart( title, JFreeChart.DEFAULT_TITLE_FONT, plot, false );
		PaintScaleLegend legend = new PaintScaleLegend( scale, new NumberAxis( ) );
		legend.setPosition( RectangleEdge.RIGHT );
		chart.addSubtitle( legend );
		return chart;
	}

	/** writes each cell's value into a heat map, e.g. for small matrices */
	public static void annotateCells( JFreeChart chart, double[][] z, String format ) {
		for ( int x = 0; x < z.length; x++ ) {
			for ( int y = 0; y < z[ x ].length; y++ ) {
				if ( Double.isFinite( z[ x ][ y ] ) ) {
					chart.getXYPlot( ).addAnnotation( new XYTextAnnotation( String.format( format, z[ x ][ y ] ), x, y ) );
				}
			}
		}
	}

	/** vertical line at x, e.g. the selected frame */
	public static void addFrameCursor( JFreeChart chart, double x ) {
		ValueMarker marker = new ValueMarker( x, MARK, new BasicStroke( 1.5f ) );
		chart.getXYPlot( ).addDomainMarker( marker );
	}

	/** shaded x interval */
	public static void addInterval( JFreeChart chart, double from, double to, Color color ) {
		chart.getXYPlot( ).addDomainMarker( new IntervalMarker( from, to, color ), org.jfree.chart.ui.Layer.BACKGROUND );
	}

	/** extra series on a second y axis (not listed in the legend) */
	public static void addSecondary( JFreeChart chart, String axisLabel, Series... series ) {
		XYPlot plot = chart.getXYPlot( );
		int index = plot.getDatasetCount( );
		plot.setDataset( index, collection( series ) );
		plot.setRangeAxis( index, new NumberAxis( axisLabel ) );
		plot.mapDatasetToRangeAxis( index, index );
		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer( true, false );
		renderer.setDefaultSeriesVisibleInLegend( false );
		renderer.setAutoPopulateSeriesPaint( false );
		renderer.setDefaultPaint( new Color( 200, 60, 60, 140 ) );
		plot.setRenderer( index, renderer );
	}

	public static ChartPanel panel( JFreeChart chart ) {
		ChartPanel panel = new ChartPanel( chart );
		panel.setMouseWheelEnabled( true );
		panel.setPreferredSize( new Dimension( 600, 260 ) );
		return panel;
	}

	/** components on top of each other, sharing the height */
	public static JComponent stack( JComponent... parts ) {
		JPanel p = new JPanel( new GridLayout( parts.length, 1, 0, 4 ) );
		for ( JComponent part : parts ) {
			p.add( part );
		}
		return p;
	}

	/** a centred text, e.g. when a step has nothing to show yet */
	public static JComponent message( String text ) {
		JLabel label = new JLabel( "<html><div style='text-align:center'>" + text + "</div></html>", SwingConstants.CENTER );
		label.setForeground( Color.DARK_GRAY );
		return label;
	}

	public static JComponent table( String[] columns, Object[][] rows ) {
		JTable table = new JTable( new DefaultTableModel( rows, columns ) {
			@Override
			public boolean isCellEditable( int row, int column ) {
				return false;
			}
		} );
		table.setAutoCreateRowSorter( true );
		return new JScrollPane( table );
	}
}
