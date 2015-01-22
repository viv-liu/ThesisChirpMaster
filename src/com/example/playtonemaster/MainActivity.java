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
    int blockSize = 512;
    boolean started = false;
    
    // Self tone
    private final int freqOfTone = 440; // hz A4
    private final int NUM_SAMPLES = sampleRate/6;//duration * sampleRate;
    private final double sample[] = new double[NUM_SAMPLES];
    private AudioTrack mAudioTrack;
    private final byte generatedSnd[] = new byte[2 * NUM_SAMPLES];
    
    private long mSendTime = 0;
    private long mReceiveTime = 0;
    private final int num_records = 10;
    private List<Long> selfTimesRecord;
    private List<Long> totalTimesRecord;
    private int beepNum = 0;
    
    private long selfSpeakerDelay = -1;
    private long rawTOF = -1;
    
    private PausableCountdownTimer timer;
    private final int TIME_BETWEEN_CHIRPS_MS = 2000;
    private long mPauseTimeLeft = num_records * TIME_BETWEEN_CHIRPS_MS;
    
    // Views
    private Button mPlayButton;
    private Button mResetButton;
    private TextView mStatusText;
    private TextView mBeepNumText;
    private TextView mTimeText;
    private TextView mSelfDelayText;
    private TextView mTotalDelayText;
    
    
    // File operations
    FileWriter writer;
    

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        genTone();
        
        selfTimesRecord = new ArrayList<Long>();
        totalTimesRecord = new ArrayList<Long>();
        
        // Initialize timer
        timer = new PausableCountdownTimer(mPauseTimeLeft, TIME_BETWEEN_CHIRPS_MS, mTimeText, mBeepNumText);
        
        // Initialize views
        mStatusText = (TextView) findViewById(R.id.textView1);
        mBeepNumText = (TextView) findViewById(R.id.textView2);
        mTimeText = (TextView) findViewById(R.id.textView3);
        mSelfDelayText = (TextView) findViewById(R.id.textView4);
        mTotalDelayText = (TextView) findViewById(R.id.textView5);
        
        mTimeText.setText(String.valueOf(mPauseTimeLeft/1000.0) + " s");
        
        mResetButton = (Button) findViewById(R.id.button2);
    	mResetButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if(mResetButton.getText().equals("Save records")) {
					// Save records
					createTimesRecordFile();
					mResetButton.setText("Reset");
				}
				mStatusText.setText("Initial state. Press play tone.");
				
				selfTimesRecord.clear();
				totalTimesRecord.clear();
				
				// Stop play tone, cancel timing, kill recordTask
				if(mAudioTrack != null) mAudioTrack.stop();
				stopTiming();
				if(recordTask != null) recordTask.cancel(false);
				
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
				mStatusText.setText("Started");
				mPlayButton.setText("Started");
	        	rawTOF = -1;
	        	selfSpeakerDelay = -1;
		        started = true;
		        recordTask = new RecordAudio();
		        recordTask.execute();
		        //playSound();
		        
				if(timer.isRunning() == false) {
					mStatusText.setText("Starting");
					timer = new PausableCountdownTimer(mPauseTimeLeft, TIME_BETWEEN_CHIRPS_MS, mTimeText, mBeepNumText);
					timer.start();
					mPlayButton.setText("Pause");
				} else if (timer.isRunning() == true) {
					mStatusText.setText("Paused");
					mPauseTimeLeft = timer.millisUntilFinished();
					timer.updatedCancel();
					mPlayButton.setText("Play");
				}
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
                sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
                AudioTrack.MODE_STATIC);
        mAudioTrack.write(generatedSnd, 0, generatedSnd.length);
    }
    
    void genTone(){
        // fill out the array
        for (int i = 0; i < NUM_SAMPLES; ++i) {
            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/freqOfTone));
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
    	beepNum++;
    	selfSpeakerDelay = -1;
    	rawTOF = -1;
    	mAudioTrack.stop();
    	mAudioTrack.reloadStaticData();
        startTiming();
        mAudioTrack.play();
    }
    
    void startTiming() {
    	mSendTime = System.nanoTime();
    }
    
    long stopTiming() {
    	mReceiveTime = System.nanoTime();
    	long delta = mReceiveTime - mSendTime;
    	return delta;
    	/*if(times_index + 1 < num_records - 1) {
    		timesRecord[times_index++] = delta;
    	}*/
    	//Log.d(TAG, "Time of flight = " + String.valueOf(delta) + "ns == " 
		//												+ String.valueOf(delta/1000000000.0) + "s");
		//Log.d(TAG, "Distance approx = " + String.valueOf(delta/1000000000.0 * SPEED_OF_SOUND));
		
    }
    
    private void createTimesRecordFile() {
    	Calendar c = Calendar.getInstance(); 
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        dateFormat.setTimeZone(c.getTimeZone());
        timeFormat.setTimeZone(c.getTimeZone());
    	String FILE_NAME = timeFormat.format(c.getTime())+".csv";
    	if (isExternalStorageWritable()) {
    		File root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS); 
    		File dir = new File (root.getAbsolutePath() + "/Thesis/" + dateFormat.format(c.getTime()));
    		//dir.mkdirs();
    		//String root = Environment.getExternalStorageDirectory().toString();
    		//File folder = new File(this.getFilesDir() + "/" + dateFormat.format(c.getTime()));    
    		
    	    //folder.mkdirs();
    		/*File folder = new File(getExternalFilesDir(
    	            Environment.DIRECTORY_DOWNLOADS), dateFormat.format(c.getTime()));*/
    	    if (!dir.exists()) {
                dir.mkdirs();
            }
    	    Toast.makeText(this, dir.toString(), Toast.LENGTH_SHORT).show();
    	    File file = new File(dir.getAbsolutePath(), FILE_NAME);
    	    try {
    	    	writer = new FileWriter(file);
	            
	            writeCsvHeader("Master selfTimesRecord", "Master totalTimesRecord");
	            for(int i = 0; i < selfTimesRecord.size(); i++) {
	            	writeCsvData(selfTimesRecord.get(i), totalTimesRecord.get(i));
	            }
	            
	            writer.flush();
	            writer.close(); 
	            Log.d("FILEIO", "FINISHED WRITING");
	            Log.d("FILEIO", "Records in " + dir.toString());
	            Toast.makeText(this, "Records written to file.", Toast.LENGTH_SHORT).show();
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
    	   String line = String.format("%s %s\n", h1,h2);
    	   writer.write(line);
    	 }

	private void writeCsvData(long selfTime, long totalTime) throws IOException {  
	  String line = String.format("%d,%d,\n", selfTime, totalTime);
	  writer.write(line);
	}
	
	private class PausableCountdownTimer extends CountDownTimer {
		private boolean mIsRunning = false;
		private long mMillisUntilFinished;
		private TextView mTimeLeftText;
		private TextView mBeepNumText;
		
		public PausableCountdownTimer(long millisInFuture,
				long countDownInterval, TextView tv, TextView tv2) {
			super(millisInFuture, countDownInterval);
			mTimeLeftText = tv;
			mBeepNumText = tv2;
			beepNum = 0;
			
		}

		@Override
		public void onTick(long millisUntilFinished) {
			recordTask.resetListening();
			mTimeLeftText.setText(String.valueOf(millisUntilFinished/1000.0) + " s");
			mIsRunning = true;
			mMillisUntilFinished = millisUntilFinished;
			playSound();
		}

		@Override
		public void onFinish() {
			mIsRunning = false;
			mMillisUntilFinished = 0;
			recordTask.cancel(false);
			mResetButton.setText("Save records");
		}
		
		public void updatedCancel() {
			super.cancel();
			mIsRunning = false;
		}

		public boolean isRunning() {
			return mIsRunning;
		}
	
		public long millisUntilFinished() {
			return mMillisUntilFinished;
		}
	}
	
	private class RecordAudio extends AsyncTask<Void, Boolean, Void> {
    	
    	private final String SELF_DETECTED_STRING = "DETECTED_SELF";
    	private final String OTHER_DETECTED_STRING = "DETECTED_OTHER";
    	
    	boolean heardSelf = false;
    	boolean heardOther = false;
    	
        @Override
        protected Void doInBackground(Void... params) {
      
        	if(isCancelled()){
        		return null;
        	}
        	//playSound();
        	List<Integer> zeroCrossingIndices = new ArrayList<Integer>();
            int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfiguration, audioEncoding);
            Log.d(SELF_DETECTED_STRING, "min buffer size = " + String.valueOf(AudioRecord.getMinBufferSize(sampleRate, channelConfiguration, audioEncoding)));
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate, channelConfiguration, audioEncoding, bufferSize);
            int bufferReadResult;
            short[] buffer = new short[blockSize];
            double[] toTransform = new double[blockSize];
            try{
            	audioRecord.startRecording();
            }
            catch(IllegalStateException e){
            	Log.e("Recording failed", e.toString());
            	
            }
            while (started) {
            	zeroCrossingIndices.clear();
            //for(int j = 0; j < 20; j++) {
            	bufferReadResult = audioRecord.read(buffer, 0, blockSize);
            
            	if(isCancelled())
                    	break;

            	double buffer_i;
            	double buffer_i_prev;
	            for (int i = 1; i < blockSize && i < bufferReadResult; i++) {
	            	buffer_i = (double) buffer[i] / 32768.0;
	            	buffer_i_prev = (double) buffer[i - 1] / 32768.0;
	            	
	            	if(buffer_i < 0.0 && buffer_i_prev > 0.0 || buffer_i > 0.0 && buffer_i_prev < 0.0) {
	            		zeroCrossingIndices.add(i);
	            		// zero crossing detected
	            		Log.d(TAG, "zero crossing at index i = " + String.valueOf(i));
	            	}
	            	
	                toTransform[i] = (double) buffer[i] / 32768.0; // signed 16 bit
	            }
	            
	            if(!heardSelf && detectTone(sampleRate, freqOfTone, zeroCrossingIndices)) {
	            	Log.e(SELF_DETECTED_STRING, "Detected self tone " + String.valueOf(freqOfTone));
	            	selfSpeakerDelay = stopTiming();
	            	heardSelf = true;
	            	publishProgress(false);
	            	selfTimesRecord.add(selfSpeakerDelay);
            	}
	            else if(!heardOther && detectTone(sampleRate, freqOfTone * 2, zeroCrossingIndices)) {
            		Log.e(OTHER_DETECTED_STRING, "Detected receive tone = " + String.valueOf(freqOfTone * 2));
            		rawTOF = stopTiming();
	        		Log.d(TAG, "rawTOF = " + String.valueOf(rawTOF/1000000.0) + "ms");
	        		heardOther = true;
	        		publishProgress(true);
	        		totalTimesRecord.add(rawTOF);
	        		
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
        protected void onProgressUpdate(Boolean... receiveComplete) {
        	mBeepNumText.setText("Beep num: " + String.valueOf(beepNum));
        	if(!receiveComplete[0]) {
    			mSelfDelayText.setText("selfSpeakerDelay = " + String.valueOf(selfSpeakerDelay/1000000.0) + "ms");
    			mTotalDelayText.setText("waiting...");
        	} else {
        		mTotalDelayText.setText("rawTOF - selfSpeakerDelay = " + String.valueOf((rawTOF - selfSpeakerDelay)/1000000.0) + "ms");
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
        private boolean detectTone(int sampleRate, int pitch, List<Integer> indices) {
        	
        	if(indices.size() <= 0) {
        		return false;
        	}
        	int CONSEC_PATTERN_THRESHOLD = (indices.size() < 20) ? indices.size() : 20;
        	double Ts = 1.0 / sampleRate;
        	double T = 1.0 / pitch;
        	
        	int numTsInT = (int) (T/Ts);
        	
        	int expectedSpacing = numTsInT / 2; // index spacing, 2 zero crossings in a single sinusoid
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
        			return false;
        		}
        		
        		if(consecCount >= CONSEC_PATTERN_THRESHOLD) {
        			Log.d("Spacing", "Woohoo conseq count = " + String.valueOf(consecCount));
        			return true;
        		}
        	}
        	return false;
        }
        public void resetListening() {
        	heardSelf = false;
        	heardOther = false;
        }
	}
}