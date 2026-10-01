package org.ioe.tprsa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class TestFiles {

	private TestFiles( ) {
	}

	/** recursive copy of a folder */
	public static void copyTree( Path from, Path to ) throws IOException {
		try ( Stream< Path > paths = Files.walk( from ) ) {
			for ( Path p : ( Iterable< Path > ) paths::iterator ) {
				Path target = to.resolve( from.relativize( p ).toString( ) );
				if ( Files.isDirectory( p ) ) {
					Files.createDirectories( target );
				} else {
					Files.copy( p, target );
				}
			}
		}
	}
}
