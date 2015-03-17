package com.example.playtonemaster;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import android.os.Environment;
import android.app.Activity;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity {
	public static final String TAG = "PlayToneMaster";
	//private final int SPEED_OF_SOUND = 344;
	
    private final int sampleRate = 8000;
    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    AudioRecord audioRecord;
    RecordAudio recordTask;
    int blockSize = 256;
    boolean started = false;
    
    // Self tone
    private final int SELF_TONE_FREQUENCY = 400; // hz A4
    private final int SELF_TONE_FREQUENCY_2 = SELF_TONE_FREQUENCY * 2;
    private final int OTHER_TONE_FREQUENCY = 500;
    private final int OTHER_TONE_FREQUENCY_2 = OTHER_TONE_FREQUENCY * 2;
    
    private final int speakerSampleRate = 8000;
    private final double outToneDurationInS = 0.125;
    private final int numTones = 12;
    private final int NUM_SAMPLES = (int) (speakerSampleRate * outToneDurationInS * numTones);
    private final double sample[] = new double[NUM_SAMPLES];
    
    private AudioTrack mAudioTrack;
    private final byte generatedSnd[] = new byte[2 * NUM_SAMPLES];
    
    private final int num_records = 3;
    private List<Short> grandBuffer;
	private List<Integer>selfToneEdges = new ArrayList<Integer>();
	private List<Integer>otherToneEdges = new ArrayList<Integer>();
	
    private PausableCountdownTimer timer;
    private final int TIME_BETWEEN_CHIRPS_MS = 500;
    private long mPauseTimeLeft = num_records * TIME_BETWEEN_CHIRPS_MS;
    
    // Views
    private Button mPlayButton;
    private Button mResetButton;
    private TextView mStatusText;
    private TextView mBeepNumText;
    private TextView mTimeText;
    private TextView mSelfToneIndexText;
    private TextView mOtherToneIndexText;
    private TextView mIndexDiffText;
    
 // Constant UI messages
    private String init_s = "Initial state, press Reset to start recording.";
    private String recording_s = "Recording in progress...";
    private String paused_s = "Paused";
    private String saving_s = "Saving buffer contents to file...";
    
    // File operations
    FileWriter writer;
    

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        genTone();
        
        grandBuffer = new ArrayList<Short>();
        // Initialize timer
        timer = new PausableCountdownTimer(mPauseTimeLeft, TIME_BETWEEN_CHIRPS_MS, mTimeText);
        
        // Initialize views
        mStatusText = (TextView) findViewById(R.id.textView1);
        mStatusText.setText(init_s);
        mBeepNumText = (TextView) findViewById(R.id.textView2);
        mTimeText = (TextView) findViewById(R.id.textView3);
        mSelfToneIndexText = (TextView) findViewById(R.id.textView4);
        mOtherToneIndexText = (TextView) findViewById(R.id.textView5);
        mIndexDiffText = (TextView) findViewById(R.id.textView6);
        
        mTimeText.setText(String.valueOf(mPauseTimeLeft/1000.0) + " s");
        
        mResetButton = (Button) findViewById(R.id.button2);
    	mResetButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				mStatusText.setText(init_s);
				mSelfToneIndexText.setText("Self tone index");
				mOtherToneIndexText.setText("Other tone index");
				mIndexDiffText.setText("Index Diff");
				
				grandBuffer.clear();
				
				// Stop play tone, cancel timing, kill recordTask
				if(mAudioTrack != null) mAudioTrack.stop();
				if(recordTask != null) {
					started = false;
					recordTask.cancel(false);
					recordTask.reset();
				}
				// Reset timer
				timer.updatedCancel();
				mPauseTimeLeft = num_records * TIME_BETWEEN_CHIRPS_MS;
				
				// Update views
				mPlayButton.setText("Play");
				mTimeText.setText(String.valueOf(mPauseTimeLeft/1000.0) + " s");
			}
	      });
    	
		mPlayButton = (Button) findViewById(R.id.button1);
		mPlayButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				mStatusText.setText(recording_s);
				if(mPlayButton.getText().equals("Play")) {
					started = true;
			        recordTask = new RecordAudio();
			        recordTask.execute();
			        //////Sub for timer tick
			        playSound();
			        mPlayButton.setText("Stop and analyze");
				} else if(mPlayButton.getText().equals("Stop and analyze")) {
					started = false;
					recordTask.cancel(true);
					bufferAnalysis();
					mPlayButton.setText("Save records");
					return;
				} else if(mPlayButton.getText().equals("Save records")) {
					// Save records
					createBigBufferFile(grandBuffer, selfToneEdges, otherToneEdges);
					mPlayButton.setText("Play");
					return;
				}
		        
		        //////////////
				/*if(timer.isRunning() == false) {
					mStatusText.setText(recording_s);
					timer = new PausableCountdownTimer(mPauseTimeLeft, TIME_BETWEEN_CHIRPS_MS, mTimeText);
					timer.start();
					mPlayButton.setText("Pause");
				} else if (timer.isRunning() == true) {
					mStatusText.setText(paused_s);
					mPauseTimeLeft = timer.millisUntilFinished();
					timer.updatedCancel();
					mPlayButton.setText("Play");
				}*/
			}
		  });
    }    

    @Override
    protected void onPause() {
    	super.onPause();
    	if(mAudioTrack != null) mAudioTrack.release();
    	if(recordTask != null) recordTask.cancel(false);
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                speakerSampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
                AudioTrack.MODE_STATIC);
        mAudioTrack.write(generatedSnd, 0, generatedSnd.length);
    }
    
    void genTone(){
    	int numSamplesPerTone = (int) (speakerSampleRate * outToneDurationInS);
    	int curFreq = SELF_TONE_FREQUENCY;
    	for(int i = 0; i < NUM_SAMPLES; i+=numSamplesPerTone) {
	        // fill out the array
	        for (int j = 0; j < numSamplesPerTone; ++j) {
	            sample[j + i] = Math.sin(2 * Math.PI * j / (speakerSampleRate/curFreq));
	        }
	        curFreq = (curFreq == SELF_TONE_FREQUENCY ? SELF_TONE_FREQUENCY_2 : SELF_TONE_FREQUENCY);
    	}

        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalized.
        int idx = 0;
        for (final double dVal : sample) {
            // scale to maximum amplitude
            final short val = (short) ((dVal * 32767));
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

        }
    }
    
    void playSound(){
    	mAudioTrack.stop();
    	mAudioTrack.reloadStaticData();
        mAudioTrack.play();
	}
	
	private class RecordAudio extends AsyncTask<Void, Boolean, Void> {
    	
    	private final int CONSEQ_COUNT_THRESHOLD = 5;
    	private int expectedSelfSpacing = expectedZerosSpacing(sampleRate, SELF_TONE_FREQUENCY);
    	private int expectedOtherSpacing = expectedZerosSpacing(sampleRate, OTHER_TONE_FREQUENCY);
    	int indexToEndRecording = -1;
    	
    	boolean heardSelf = false;
    	boolean heardOther = true;
    	
    	
        @Override
        protected Void doInBackground(Void... params) {
      
        	if(isCancelled()){
        		return null;
        	}
        	//playSound();
        	List<Integer> zeroCrossingIndices = new ArrayList<Integer>();
            int bufferSize = 60000;//AudioRecord.getMinBufferSize(sampleRate, channelConfiguration, audioEncoding);
            
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate, channelConfiguration, audioEncoding, bufferSize);
            int bufferReadResult;
            short[] buffer = new short[blockSize];
            //double[] toTransform = new double[bufferSize];
            try{
            	audioRecord.startRecording();
            }
            catch(IllegalStateException e){
            	Log.e("Recording failed", e.toString());
            	
            }
            while (started) {
            	int i_lastZero = 0;
            	int conseqCount = 0;
	            
            	zeroCrossingIndices.clear();
            	bufferReadResult = audioRecord.read(buffer, 0, blockSize);
            
            	if(isCancelled())
                    	break;
            	
	            for (int i = 1; i < blockSize && i < bufferReadResult; i++) {
	            	if(grandBuffer.size() <= 100000)
	            		grandBuffer.add(buffer[i-1]);
	           
	            	if(indexToEndRecording > 0 && grandBuffer.size() >= indexToEndRecording) {
	            		indexToEndRecording = -1;
	            		publishProgress(true);
	            		break;
	            	}
	            	
	            	if((conseqCount < CONSEQ_COUNT_THRESHOLD) &&
	            		((buffer[i] < 0 && buffer[i-1] > 0) || (buffer[i] > 0 && buffer[i-1] < 0))) {

	            		if(!heardSelf) {
	            			if(((i-i_lastZero) >= (expectedSelfSpacing - 1)) && 
	            					((i-i_lastZero) <= (expectedSelfSpacing + 1))) {
	            				conseqCount++;
	            				//Log.d(TAG, "Good spacing " + String.valueOf(i));
	            			} else {
	            				conseqCount = 0;
	            				//Log.d(TAG, "Bad spacing " + String.valueOf(i));
	            			}
	            			if(conseqCount >= CONSEQ_COUNT_THRESHOLD) {
	            				heardSelf = true;
	            				heardOther = false;
		            		}
	            		} else if(!heardOther) {
	            			if(((i-i_lastZero) >= (expectedOtherSpacing - 1)) && 
	            					((i-i_lastZero) <= (expectedOtherSpacing + 1))) {
	            				conseqCount++;
	            			} else {
	            				conseqCount = 0;
	            			}
	            			if(conseqCount >= CONSEQ_COUNT_THRESHOLD) {
	            				heardOther = true;
	            				indexToEndRecording = grandBuffer.size() + (int) (sampleRate * outToneDurationInS * numTones);
	            			}
	            		}
	            		i_lastZero = i;
	            	}
	            }
	            
	            if(isCancelled())
	            	break;
            }
            
            try{
            	audioRecord.stop();
            }
            catch(IllegalStateException e){
            	Log.e("Stop failed", e.toString());
            	
            }               
            
            return null;
        }
        
        @Override
		protected void onProgressUpdate(Boolean... isRecordingFinished) {
        	if(isRecordingFinished[0]) {
				mPlayButton.setText("Stop and analyze");
				mPlayButton.performClick();
        	}
		}
        
        protected void onPostExecute(Void result) {
        	try{
            	audioRecord.stop();
            }
            catch(IllegalStateException e){
            	Log.e("Stop failed", e.toString());
            	
            }
        	recordTask.cancel(true); 
        	
        	Intent intent = new Intent(Intent.ACTION_MAIN);
        	intent.addCategory(Intent.CATEGORY_HOME);
        	intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        	startActivity(intent);
        }
        
        /*
         * ONLY DETECT TONES THAT ARE MULTIPLES OF EACH OTHER
         * PRONE TO SAY FALSE THAN TRUE
         */
        /**
         * Detect the presence of a tone and returns the index of beginning of tone.
         * @param sampleRate
         * @param pitch
         * @param indices
         * @return index of beginning of tone in the 512 buffer
         */
        private int detectTone(int sampleRate, int pitch, List<Integer> indices) {
        	double Ts = 1.0 / sampleRate;
        	double T = 1.0 / pitch;
        	int numTsInT = (int) (T/Ts);
        	int expectedSpacing = numTsInT / 2; // index spacing, 2 zero crossings in a single sinusoid
        	
        	int CONSEC_PATTERN_THRESHOLD = blockSize/expectedSpacing - 2;
        	Log.d("Spacing", "Consec pattern threshold = " + String.valueOf(CONSEC_PATTERN_THRESHOLD));
        	Log.d(TAG, "Expected spacing for " + String.valueOf(pitch) + " = " + String.valueOf(expectedSpacing));
        	int diff = 0;
        	int consecCount = 0;
        	
        	for(int i = 1; i < indices.size(); i++) {
        		diff = indices.get(i) - indices.get(i-1);
        		if((diff >= (expectedSpacing - 1)) && (diff <= (expectedSpacing + 1))) {
        			consecCount++;
        			Log.d("Spacing", "Spacing ok, index " + String.valueOf(i) + " = " + String.valueOf(indices.get(i)));
        		} else {
        			Log.e("Spacing", "Failed, index " + String.valueOf(i) + " = " + String.valueOf(indices.get(i)));
        			return -1;
        		}
        		
        		if(consecCount >= CONSEC_PATTERN_THRESHOLD) {
        			Log.d("Spacing", "Woohoo conseq count = " + String.valueOf(consecCount));
        			return indices.get(i-consecCount);
        		}
        	}
        	return -1;
        }
        public void reset() {
        	heardSelf = false;
        	heardOther = false;
        }
	}
	
/*************************************************************
 * DATA ANALYSIS
 *************************************************************/
	private int expectedZerosSpacing(int sampleRate, int pitch) {
    	double Ts = 1.0 / sampleRate;
    	double T = 1.0 / pitch;
    	int numTsInT = (int) (T/Ts);
    	int expectedSpacing = numTsInT / 2;
    	return expectedSpacing;
    }
	private void bufferAnalysis() {
		int expectedSelfSpacing = expectedZerosSpacing(sampleRate, SELF_TONE_FREQUENCY);
		int expectedSelfSpacing_2 = expectedZerosSpacing(sampleRate, SELF_TONE_FREQUENCY_2);
    	
    	int expectedSpacing_1 = expectedSelfSpacing;
    	int expectedSpacing_2 = expectedSelfSpacing_2;
    	int listeningFor = 1;
    	
		final int CONSEQ_COUNT_THRESHOLD = 10;
		int i_lastZero = 0;
    	int conseqCount = 0;
    	
    	// Contains the index of the edge of each occurence of the second tone in each captured tone pair
    	selfToneEdges.clear();
    	otherToneEdges.clear();
    	
		for (int i = 1; i < grandBuffer.size(); i++) {
			
        	if((conseqCount < CONSEQ_COUNT_THRESHOLD) &&
        		((grandBuffer.get(i) < 0 && grandBuffer.get(i-1) > 0) || (grandBuffer.get(i) > 0 && grandBuffer.get(i-1) < 0))) {
        		//Log.d(TAG, "Found a zero at index " + String.valueOf(i));
        		if(listeningFor == 1) {
        			Log.d(TAG, "Listening for 1");
        			if(((i-i_lastZero) >= (expectedSpacing_1 - 1)) && 
        					((i-i_lastZero) <= (expectedSpacing_1 + 1))) {
        				conseqCount++;
        				//Log.d(TAG, "Good spacing " + String.valueOf(i));
        			} else {
        				conseqCount = 0;
        				//Log.d(TAG, "Bad spacing " + String.valueOf(i));
        			}
        			if(conseqCount >= CONSEQ_COUNT_THRESHOLD) {
        				Log.d(TAG, "Transitioning to 2");
        				//heard first tone
        				listeningFor = 2;
            			conseqCount = 0;
            		}
        			
        		} else if(listeningFor == 2) {
        			Log.d(TAG, "Listening for 2");
        			if(((i-i_lastZero) >= (expectedSpacing_2 - 1)) && 
        					((i-i_lastZero) <= (expectedSpacing_2 + 1))) {
        				
        				if(selfToneEdges.size() < numTones/2) {
        					selfToneEdges.add(i);
        					Log.d(TAG, "Add to self");
        					if(selfToneEdges.size() >= numTones/2) {
        						expectedSpacing_1 = expectedZerosSpacing(sampleRate, OTHER_TONE_FREQUENCY);
            					expectedSpacing_2 = expectedZerosSpacing(sampleRate, OTHER_TONE_FREQUENCY_2);
            					Log.d(TAG, "Change expectations");
        					}
        				} else if(selfToneEdges.size() >= numTones/2 && otherToneEdges.size() < numTones/2) {
        					otherToneEdges.add(i);
        					Log.d(TAG, "Add to other");
        					if(otherToneEdges.size() >= numTones/2) {
        						Log.d(TAG, "break");
            					break;
        					}
        					
        				}
        				listeningFor = 1;
        			}
        		}
        		i_lastZero = i;
        	}
        }
		if(selfToneEdges.size() >= numTones / 2 && otherToneEdges.size() >= numTones / 2) {
			mSelfToneIndexText.setText("Self tone index: " + String.valueOf(selfToneEdges.get(0)) + " ,"
					+ String.valueOf(selfToneEdges.get(1)) + " ,"
					+ String.valueOf(selfToneEdges.get(2)));
			mOtherToneIndexText.setText("Other tone index: " + String.valueOf(otherToneEdges.get(0)) + " ,"
					+ String.valueOf(otherToneEdges.get(1)) + " ,"
					+ String.valueOf(otherToneEdges.get(2)));
			mIndexDiffText.setText("Index Diff: " + String.valueOf(otherToneEdges.get(0) - selfToneEdges.get(0)) + " ,"
					+ String.valueOf(otherToneEdges.get(1) - selfToneEdges.get(1)) + " ,"
					+ String.valueOf(otherToneEdges.get(2) - selfToneEdges.get(2)));
		}
	}
/*************************************************************
 * FILE IO OPERATIONS
 *************************************************************/
    /**
     * Write all grandBuffer contents to a csv file.
     * @param buffer
     */
    private void createBigBufferFile(List<Short> buffer, List<Integer>selfEdges, List<Integer>otherEdges) {
    	Calendar c = Calendar.getInstance(); 
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        dateFormat.setTimeZone(c.getTimeZone());
        timeFormat.setTimeZone(c.getTimeZone());
    	String FILE_NAME = timeFormat.format(c.getTime())+"_MasterRecord.csv";
    	if (isExternalStorageWritable()) {
    		File root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS); 
    		File dir = new File (root.getAbsolutePath() + "/Thesis/" + dateFormat.format(c.getTime()));
    	    if (!dir.exists()) {
                dir.mkdirs();
            }
    	    //Toast.makeText(this, dir.toString(), Toast.LENGTH_SHORT).show();
    	    File file = new File(dir.getAbsolutePath(), FILE_NAME);
    	    try {
    	    	writer = new FileWriter(file);
    	    	writeCsvHeader("Self edges", "Other edges");
    	    	for(int i = 0; i < Math.min(selfEdges.size(), otherEdges.size()); i++) {
    	    		writeCsvHeader(String.valueOf(selfEdges.get(i)), String.valueOf(otherEdges.get(i)));
    	    	}
    	    	// Write contents of buffer in
	            for(int i = 0; i < buffer.size(); i++) {
	            	String line = buffer.get(i).toString() + "\n";
	          	  	writer.write(line);
	            }
	            
	            writer.flush();
	            writer.close(); 
	            Log.d("FILEIO", "FINISHED WRITING");
	            Toast.makeText(this, "Buffer written to file.", Toast.LENGTH_SHORT).show();
	            mStatusText.setText(init_s);
    	    } catch (IOException e) {
    	        e.printStackTrace();
    	    }    
    	} else {
    	
    		Log.d("ERROR", "External storage not writable");
    	}
    }
    private void createBigDiffFile(List<Integer> buffer) {
    	Calendar c = Calendar.getInstance(); 
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        dateFormat.setTimeZone(c.getTimeZone());
        timeFormat.setTimeZone(c.getTimeZone());
    	String FILE_NAME = timeFormat.format(c.getTime())+"_DiffList.csv";
    	if (isExternalStorageWritable()) {
    		File root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS); 
    		File dir = new File (root.getAbsolutePath() + "/Thesis/" + dateFormat.format(c.getTime()));
    	    if (!dir.exists()) {
                dir.mkdirs();
            }
    	    //Toast.makeText(this, dir.toString(), Toast.LENGTH_SHORT).show();
    	    File file = new File(dir.getAbsolutePath(), FILE_NAME);
    	    try {
    	    	writer = new FileWriter(file);
	            
    	    	// Write contents of buffer in
	            for(int i = 0; i < buffer.size(); i++) {
	            	String line = buffer.get(i).toString() + "\n";
	          	  	writer.write(line);
	            }
	            
	            writer.flush();
	            writer.close(); 
	            Log.d("FILEIO", "FINISHED WRITING");
	            Toast.makeText(this, "Buffer written to file.", Toast.LENGTH_SHORT).show();
    	    } catch (IOException e) {
    	        e.printStackTrace();
    	    }    
    	} else {
    	
    		Log.d("ERROR", "External storage not writable");
    	}
    }
    
    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private void writeCsvHeader(String h1, String h2) throws IOException {
    	// Writes a space to avoid messin the first column
    	   String line = String.format(" ,%s,%s\n", h1,h2);
    	   writer.write(line);
    	 }

	private void writeCsvData(long selfTime, long totalTime) throws IOException {  
	  String line = String.format("%d,%d\n", selfTime, totalTime);
	  writer.write(line);
	}
}