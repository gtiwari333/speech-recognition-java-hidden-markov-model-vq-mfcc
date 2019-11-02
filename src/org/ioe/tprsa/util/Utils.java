package org.ioe.tprsa.util;

public class Utils {

	public static boolean isNotEmpty( String str ) {

		return !isEmpty( str );
	}

	public static boolean isEmpty( String str ) {

		return ( str == null || str.trim( ).length( ) == 0 );
	}

	public static String clean( String str ) {

		return ( str == null ? "" : str.trim( ) );
	}

}
