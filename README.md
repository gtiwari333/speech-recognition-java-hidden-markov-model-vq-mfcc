## Java Speech Recognition using Hidden Markov Model / Vector Quantization / MFCC 


Automatically exported from code.google.com/p/speech-recognition-java-hidden-markov-model-vq-mfcc

===

#### Accompanying project report

- http://ganeshtiwaridotcomdotnp.blogspot.com/2011/06/final-report-text-prompted-remote.html

#### Main classes to look into are :

- org.ioe.tprsa.mediator.Operations : class that demonstrates use of HMM, VQ training and testing

- org.ioe.tprsa.ui.HMM_VQ_Speech_Recognition : GUI that allows record of voice samples per word, train the samples, test with a recorded audio or saved .wav file.


##### Folder conventions :

##### Training audio files 
-	speechTrainWav\apple\apple01.wav
-	speechTrainWav\apple\apple02.wav
-	speechTrainWav\apple\apple03.wav
-	speechTrainWav\apple\apple04.wav
-	speechTrainWav\cat\cat01.wav
-	speechTrainWav\cat\cat02.wav
-	speechTrainWav\cat\cat03.wav
	
###### Vector Quantization code book :	
-	models\codeBook\codebook.cbk
	
###### HMM trained models :	
-	models\HMM\apple.hmm
-	models\HMM\cat.hmm
-	models\GMM\Nepal.hmm


### Please feel free to use/modify the code !
  
### For any queries :
 
##### Email : gtiwari333@gmail.com
##### Blog : http://ganeshtiwaridotcomdotnp.blogspot.com/ 
