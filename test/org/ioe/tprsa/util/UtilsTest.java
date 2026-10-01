package org.ioe.tprsa.util;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;

import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {

	@Test
	void emptiness( ) {
		assertTrue( Utils.isEmpty( null ) );
		assertTrue( Utils.isEmpty( "  " ) );
		assertFalse( Utils.isEmpty( "a" ) );
		assertTrue( Utils.isNotEmpty( " a " ) );
	}

	@Test
	void clean( ) {
		assertEquals( "", Utils.clean( null ) );
		assertEquals( "abc", Utils.clean( "  abc " ) );
	}

	@Test
	void errorManagerReportsToLabel( ) {
		JLabel lbl = new JLabel( );
		ErrorManager.setMessageLbl( lbl );
		ErrorManager.reportStatus( "hello", MessageType.values( )[ 0 ] );
		assertEquals( "hello", lbl.getText( ) );
	}
}
