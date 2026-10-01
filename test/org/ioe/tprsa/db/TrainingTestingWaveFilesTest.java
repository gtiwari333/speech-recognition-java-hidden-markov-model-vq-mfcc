package org.ioe.tprsa.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrainingTestingWaveFilesTest {

	private static TrainingTestingWaveFiles layout( Path dir ) throws Exception {
		for ( String word : new String[] { "apple", "cat" } ) {
			Files.createDirectories( dir.resolve( word ) );
			Files.createFile( dir.resolve( word ).resolve( word + "0.wav" ) );
			Files.createFile( dir.resolve( word ).resolve( word + "1.wav" ) );
		}
		TrainingTestingWaveFiles t = new TrainingTestingWaveFiles( "train" );
		t.setWavPath( dir.toFile( ) );
		return t;
	}

	@Test
	void defaultsToTrainWavFolder( ) {
		assertEquals( new File( "TrainWav" ), new TrainingTestingWaveFiles( "train" ).getWavPath( ) );
		assertEquals( new File( "TestWav" ), new TrainingTestingWaveFiles( "test" ).getWavPath( ) );
	}

	@Test
	void listsWordFoldersAndTheirFiles( @TempDir Path dir ) throws Exception {
		TrainingTestingWaveFiles t = layout( dir );
		List< String > words = t.readWordWavFolder( );
		File[][] files = t.readWaveFilesList( );

		assertEquals( List.of( "apple", "cat" ), words.stream( ).sorted( ).toList( ) );
		for ( int i = 0; i < words.size( ); i++ ) {
			assertEquals( 2, files[ i ].length );
			for ( File f : files[ i ] ) {
				assertTrue( f.getName( ).startsWith( words.get( i ) ), "files must line up with the word at the same index" );
			}
		}
	}

	@Test
	void ignoresStrayFiles( @TempDir Path dir ) throws Exception {
		TrainingTestingWaveFiles t = layout( dir );
		Files.createFile( dir.resolve( ".DS_Store" ) );
		assertEquals( 2, t.readWaveFilesList( ).length );
	}
}
