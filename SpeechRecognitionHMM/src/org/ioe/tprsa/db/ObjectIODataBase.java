/*
  Please feel free to use/modify this class. 
  If you give me credit by keeping this information or
  by sending me an email before using it or by reporting bugs , i will be happy.
  Email : gtiwari333@gmail.com,
  Blog : http://ganeshtiwaridotcomdotnp.blogspot.com/ 
 */
package org.ioe.tprsa.db;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ioe.tprsa.classify.speech.CodeBookDictionary;
import org.ioe.tprsa.classify.speech.HMMModel;

/**
 * @author Ganesh Tiwari
 */
public class ObjectIODataBase implements DataBase {

	/**
	 * type of current model,,gmm,hmm,cbk, which is extension ofsaved file
	 */
	String			type;
	/**
	 *
	 */
	List< String >	modelFiles;
	/**
	 *
	 */
	String[]		userNames;
	String			CURRENTFOLDER;
	/**
	 * the file name to same codebook, adds .cbk extension automatically
	 */
	String			CODEBOOKFILENAME	= "codebook";
	String			currentModelType;

	/**
	 * MAKE SURE THAT Files are/will be in this folder structure the folder structure for training : (Selected)DBROOTFOLDER\ \speechTrainWav\\apple\\apple01.wav
	 * \speechTrainWav\\apple\\apple02.wav \speechTestWav\\cat\\cat01.wav \speechTestWav\\cat\\cat01.wav \speechTestWav\\cat\\cat01.wav \codeBook\\codeBook.cbk
	 * \models\\HMM\\apple.hmm \models\\HMM\\cat.hmm \models\\GMM\\ram.gmm \models\\GMM\\shyam.gmm
	 */
	public ObjectIODataBase( ) {
	}

	/**
	 * @param type
	 *            type of the model, valid entry are either gmm, hmm, or cbk
	 */
	public void setType( String type ) {
		this.type = type;
		if ( this.type.equalsIgnoreCase( "hmm" ) ) {
			CURRENTFOLDER = "models" + File.separator + "HMM";
		}
		if ( this.type.equalsIgnoreCase( "cbk" ) ) {
			CURRENTFOLDER = "models" + File.separator + "codeBook";
		}
	}

	/**
	 *
	 */
	@Override
	public Model readModel( String name ) throws Exception {
		Model model = null;
		if ( type.equalsIgnoreCase( "hmm" ) ) {
			ObjectIO< HMMModel > oio = new ObjectIO< HMMModel >( );
			model = new HMMModel( );
			model = oio.readModel( CURRENTFOLDER + File.separator + name + "." + type );
			//            System.out.println("Type " + type);
			//            System.out.println("Read ::::: " + DBROOTFOLDER + "\\" + CURRENTFOLDER + "\\" + name + "." + type);
			// System.out.println(model);
		}
		if ( type.equalsIgnoreCase( "cbk" ) ) {
			ObjectIO< CodeBookDictionary > oio = new ObjectIO< CodeBookDictionary >( );
			model = new CodeBookDictionary( );
			model = oio.readModel( CURRENTFOLDER + File.separator + CODEBOOKFILENAME + "." + type );
			//            System.out.println("Read ::::: " + DBROOTFOLDER + "\\" + CURRENTFOLDER + "\\" + CODEBOOKFILENAME + "." + type);
		}
		return model;
	}

	/**
	 *
	 */
	@Override
	public List< String > readRegistered( ) {

		modelFiles = readRegisteredWithExtension( );
		System.out.println( "modelFiles length (Oiodb) :" + modelFiles.size( ) );
		return removeExtension( modelFiles );
	}

	/**
	 *
	 */
	@Override
	public void saveModel( Model model, String name ) throws Exception{

		if ( type.equalsIgnoreCase( "hmm" ) ) {
			ObjectIO< HMMModel > oio = new ObjectIO< HMMModel >( );
			oio.setModel( ( HMMModel ) model );
			oio.saveModel( CURRENTFOLDER + File.separator + name + "." + type );
		}
		if ( type.equalsIgnoreCase( "cbk" ) ) {
			ObjectIO< CodeBookDictionary > oio = new ObjectIO< CodeBookDictionary >( );
			oio.setModel( ( CodeBookDictionary ) model );
			oio.saveModel( CURRENTFOLDER + File.separator + CODEBOOKFILENAME + "." + type );
		}

	}

	private List< String > readRegisteredWithExtension( ) {
		File modelPath = new File( CURRENTFOLDER );

		modelFiles = Arrays.asList( modelPath.list( ) );// must return only folders

		return modelFiles;
	}

	private String removeExtension( String fileName ) {

		return fileName.substring( 0, fileName.lastIndexOf( '.' ) );

	}

	private List< String > removeExtension( List< String > modelFiles ) {
		// remove the ext i.e., type
		List< String > noExtension = new ArrayList< >( );
		for ( String fileName: modelFiles ) {
			noExtension.add( removeExtension( fileName ) );// TODO:check the lengths
		}

		return noExtension;
	}
}
