package org.ioe.tprsa.ui.viz;

import org.jfree.chart.ChartPanel;

import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

final class ViewTestSupport {

	private ViewTestSupport( ) {
	}

	/** renders every chart inside the component to an image; returns the number of charts */
	static int renderAll( Component c ) {
		int count = 0;
		if ( c instanceof ChartPanel panel ) {
			BufferedImage img = panel.getChart( ).createBufferedImage( 640, 400 );
			assertEquals( 640, img.getWidth( ) );
			count++;
		}
		if ( c instanceof Container container ) {
			for ( Component child : container.getComponents( ) ) {
				count += renderAll( child );
			}
		}
		return count;
	}
}
