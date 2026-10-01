package org.ioe.tprsa.mediator;

import org.ioe.tprsa.TestFiles;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag( "integration" )
class OperationsBaseDirTest {

	/** every file in the project's models/ folder must be byte-identical to the one trained into dir */
	static void assertModelsIdentical( Path dir ) throws Exception {
		Path models = Paths.get( "models" );
		try ( Stream< Path > files = Files.walk( models ) ) {
			for ( Path f : ( Iterable< Path > ) files.filter( Files::isRegularFile )::iterator ) {
				Path trained = dir.resolve( "models" ).resolve( models.relativize( f ).toString( ) );
				assertTrue( Files.exists( trained ), "not trained: " + trained );
				assertArrayEquals( Files.readAllBytes( f ), Files.readAllBytes( trained ),
						f + " differs (retrain models/ if TrainWav/ changed)" );
			}
		}
	}

	@Test
	void trainsIntoTheBaseDirAndReproducesTheProjectModels( @TempDir Path dir ) throws Exception {
		TestFiles.copyTree( Paths.get( "TrainWav" ), dir.resolve( "TrainWav" ) );
		Operations op = new Operations( dir );
		op.generateCodebook( );
		op.hmmTrain( );
		assertModelsIdentical( dir );
		assertEquals( "Apple", op.hmmGetWordFromFile( new File( "TrainWav/Apple/Apple0.wav" ) ) );
	}

	@Test
	void generatingTheCodebookTwiceGivesTheSameCodebook( @TempDir Path dir ) throws Exception {
		TestFiles.copyTree( Paths.get( "TrainWav" ), dir.resolve( "TrainWav" ) );
		Path codebook = dir.resolve( "models/codeBook/codebook.cbk" );
		Operations op = new Operations( dir );
		op.generateCodebook( );
		byte[] first = Files.readAllBytes( codebook );
		op.generateCodebook( );
		assertArrayEquals( first, Files.readAllBytes( codebook ) );
	}
}
