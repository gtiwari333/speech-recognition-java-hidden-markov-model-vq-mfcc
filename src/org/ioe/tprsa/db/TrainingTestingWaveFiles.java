/*
  Please feel free to use/modify this class. 
  If you give me credit by keeping this information or
  by sending me an email before using it or by reporting bugs , i will be happy.
  Email : gtiwari333@gmail.com,
  Blog : http://ganeshtiwaridotcomdotnp.blogspot.com/ 
 */
package org.ioe.tprsa.db;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * various operations relating to reading train/testing wav folders<br>
 * works according to the filePath supplied in constructor arguement
 * 
 * @author Ganesh Tiwari
 */
public class TrainingTestingWaveFiles {

	protected List< String >	folderNames;
	protected File[][]			waveFiles;
	protected File				wavPath;

	/**
	 * constructor, sets the wavFile path according to the args supplied
	 * 
	 * @param testOrTrain
	 */
	public TrainingTestingWaveFiles( String testOrTrain ) {
		if ( testOrTrain.equalsIgnoreCase( "test" ) ) {
			setWavPath( new File( "TestWav" ) );
		} else if ( testOrTrain.equalsIgnoreCase( "train" ) ) {
			setWavPath( new File( "TrainWav" ) );
		}

	}

	private void readFolder( ) {
		//		System.out.println(getWavPath().getAbsolutePath());
		folderNames = Arrays.asList( getWavPath( ).list( ) );// must return only folders
	}

	public List< String > readWordWavFolder( ) {
		readFolder( );
		return folderNames;
	}

	public File[][] readWaveFilesList( ) {
		readFolder( );
		waveFiles = new File[ folderNames.size( ) ][];
		for ( int i = 0; i < folderNames.size( ); i++ ) {

			System.out.println( folderNames.get( i ) );
			File wordDir = new File( getWavPath( ) + File.separator + folderNames.get( i ) + File.separator );
			waveFiles[ i ] = wordDir.listFiles( );
		}
		System.out.println( "++++++Folder's Content+++++" );
		for (File[] waveFile : waveFiles) {
			for (int j = 0; j < waveFile.length; j++) {
				System.out.print(waveFile[j].getName() + "\t\t");
			}
			System.out.println();
		}
		return waveFiles;

	}

	public File getWavPath( ) {
		return wavPath;
	}

	public void setWavPath( File wavPath ) {
		this.wavPath = wavPath;
		System.out.println( "Current wav file Path   :" + this.wavPath.getName( ) );
	}
}
