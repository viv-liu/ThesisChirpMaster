package com.example.playtonemaster;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import android.os.Environment;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity {
	public static final String TAG = "PlayToneMaster";
	private final int SPEED_OF_SOUND = 343;
	
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
    private final double outToneDurationInS = 0.5;
    private final int numTones = 6;
    private final int NUM_SAMPLES = (int) (speakerSampleRate * outToneDurationInS * numTones);
    private final double sample[] = new double[NUM_SAMPLES];
    
    private AudioTrack mAudioTrack;
    private final byte generatedSnd[] = new byte[2 * NUM_SAMPLES];
    
    private final int num_records = 3;
    private List<Short> grandBuffer;
	private List<Integer>selfToneEdges = new ArrayList<Integer>();
	private List<Integer>otherToneEdges = new ArrayList<Integer>();
    
	private double finalResult;
    // Views
    private Button mPlayButton;
    private Button mResetButton;
    private TextView mStatusText;
    private TextView mBeepNumText;
    private TextView mDistanceText;
    private TextView mSelfToneIndexText;
    private TextView mOtherToneIndexText;
    private TextView mIndexDiffText;
    
    // Constant UI messages
    private String init_s = "Initial state, press Reset to start recording.";
    private String recording_s = "Recording in progress...";
    private String paused_s = "Paused";
    private String saving_s = "Saving buffer contents to file...";
    
    // Bluetooth
 	private static final boolean D = true;

 	// Message types sent from the BluetoothChatService Handler
 	public static final int MESSAGE_STATE_CHANGE = 1;
 	public static final int MESSAGE_READ = 2;
 	public static final int MESSAGE_WRITE = 3;
 	public static final int MESSAGE_DEVICE_NAME = 4;
 	public static final int MESSAGE_TOAST = 5;

 	// Key names received from the BluetoothChatService Handler
 	public static final String DEVICE_NAME = "device_name";
 	public static final String TOAST = "toast";

 	// Intent request codes
 	private static final int REQUEST_CONNECT_DEVICE = 2;
 	private static final int REQUEST_ENABLE_BT = 3;

	// Name of the connected device
	private String mConnectedDeviceName = null;
	// String buffer for outgoing messages
	private StringBuffer mOutStringBuffer;
	// Local Bluetooth adapter
	private BluetoothAdapter mBluetoothAdapter = null;
	// Member object for the chat services
	private BluetoothChatService mChatService = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        genTone();
       
		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// If the adapter is null, then Bluetooth is not supported
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available",
					Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		
        grandBuffer = new ArrayList<Short>();
        
        // Initialize views
        mStatusText = (TextView) findViewById(R.id.textView1);
        mStatusText.setText(init_s);
        mBeepNumText = (TextView) findViewById(R.id.textView2);
        mDistanceText = (TextView) findViewById(R.id.textView3);
        mSelfToneIndexText = (TextView) findViewById(R.id.textView4);
        mOtherToneIndexText = (TextView) findViewById(R.id.textView5);
        mIndexDiffText = (TextView) findViewById(R.id.textView6);
        
        mDistanceText.setText("Speed of sound statically set to 343 m/s");
        
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
				
				// Update views
				mPlayButton.setText("Play");
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
			}
		  });
    }    

    @Override
	public void onStart() {
		super.onStart();
		if (D)
			Log.e(TAG, "++ ON START ++");

		// If BT is not on, request that it be enabled.
		// setupChat() will then be called during onActivityResult
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			// Otherwise, setup the chat session
		} else {
			if (mChatService == null)
				setupChat();
		}
	}

	@Override
	public synchronized void onResume() {
		super.onResume();
		if (D)
			Log.e(TAG, "+ ON RESUME +");

		mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                speakerSampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
                AudioTrack.MODE_STATIC);
        mAudioTrack.write(generatedSnd, 0, generatedSnd.length);
        
		// Performing this check in onResume() covers the case in which BT was
		// not enabled during onStart(), so we were paused to enable it...
		// onResume() will be called when ACTION_REQUEST_ENABLE activity
		// returns.
		if (mChatService != null) {
			// Only if the state is STATE_NONE, do we know that we haven't
			// started already
			if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
				// Start the Bluetooth chat services
				mChatService.start();
			}
		}
	}

	private void setupChat() {
		Log.d(TAG, "setupChat()");

		// Initialize the BluetoothChatService to perform bluetooth connections
		mChatService = new BluetoothChatService(this, mHandler);

		// Initialize the buffer for outgoing messages
		mOutStringBuffer = new StringBuffer("");
	}
	
    @Override
    protected void onPause() {
    	super.onPause();
    	if(mAudioTrack != null) mAudioTrack.release();
    	if(recordTask != null) recordTask.cancel(false);
    }
    
    List<Double> createOutputToneArray(int baseFrequency, String optionalFileName) {
    	List<Double> toneArray = new ArrayList<Double>();
    	int numSamplesPerTone = (int) (speakerSampleRate * outToneDurationInS);
    	int curFreq = baseFrequency;
    	for(int i = 0; i < NUM_SAMPLES; i+=numSamplesPerTone) {
	        // fill out the array
	        for (int j = 0; j < numSamplesPerTone; ++j) {
	            toneArray.add(Math.sin(2 * Math.PI * j / (speakerSampleRate/curFreq)));
	        }
	        curFreq = (curFreq == baseFrequency ? baseFrequency * 2 : baseFrequency);
    	}
    	if(optionalFileName != null) {
    		createBufferFile(toneArray, optionalFileName);
    	}
    	return toneArray;
    }
    List<Double> createOutputToneArrayAmplitudeVarying(int baseFrequency, String optionalFileName) {
    	List<Double> toneArray = new ArrayList<Double>();
    	int numSamplesPerTone = (int) (speakerSampleRate * outToneDurationInS);
    	int curFreq = baseFrequency;
    	int curAmplitude = 1;
    	for(int i = 0; i < NUM_SAMPLES; i+=numSamplesPerTone) {
	        // fill out the array
	        for (int j = 0; j < numSamplesPerTone; ++j) {
	            toneArray.add(curAmplitude * Math.sin(2 * Math.PI * j / (speakerSampleRate/curFreq)));
	        }
	        curFreq = (curFreq == baseFrequency ? baseFrequency * 2 : baseFrequency);
	        curAmplitude = (curFreq == baseFrequency ? 1 : 4);
    	}
    	if(optionalFileName != null) {
    		createBufferFile(toneArray, optionalFileName);
    	}
    	return toneArray;
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
	
    @Override
	public void onDestroy() {
		super.onDestroy();
		// Stop the Bluetooth chat services
		if (mChatService != null)
			mChatService.stop();
		if (D)
			Log.e(TAG, "--- ON DESTROY ---");
	}

	private void ensureDiscoverable() {
		if (D)
			Log.d(TAG, "ensure discoverable");
		if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			Intent discoverableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(
					BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			startActivity(discoverableIntent);
		}
	}
	
	private final void setStatus(int resId) {
		final ActionBar actionBar = getActionBar();
		actionBar.setSubtitle(resId);
	}

	private final void setStatus(CharSequence subTitle) {
		final ActionBar actionBar = getActionBar();
		actionBar.setSubtitle(subTitle);
	}
	
	// The Handler that gets information back from the BluetoothChatService
		private final Handler mHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case MESSAGE_STATE_CHANGE:
					if (D)
						Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
					switch (msg.arg1) {
					case BluetoothChatService.STATE_CONNECTED:
						setStatus(getString(R.string.title_connected_to,
								mConnectedDeviceName));
						break;
					case BluetoothChatService.STATE_CONNECTING:
						setStatus(R.string.title_connecting);
						break;
					case BluetoothChatService.STATE_LISTEN:
					case BluetoothChatService.STATE_NONE:
						setStatus(R.string.title_not_connected);
						break;
					}
					break;
				case MESSAGE_WRITE:
					byte[] writeBuf = (byte[]) msg.obj;
					// construct a string from the buffer
					String writeMessage = new String(writeBuf);
					break;
				case MESSAGE_READ:
					byte[] readBuf = (byte[]) msg.obj;
					// construct a string from the valid bytes in the buffer
					String readMessage = new String(readBuf, 0, msg.arg1);
					Toast.makeText(MainActivity.this, readMessage, Toast.LENGTH_LONG).show();
					double diff = SPEED_OF_SOUND * Math.abs(finalResult - Double.parseDouble(readMessage))/sampleRate * 100/2;
					if(diff > 150) {
						mDistanceText.setText("Oops I think I measured something wrong, please try again.");
					} else {
						mDistanceText.setText(String.format( "%.2f", diff) + " cm");
					}
					break;
				case MESSAGE_DEVICE_NAME:
					// save the connected device's name
					mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
					Toast.makeText(getApplicationContext(),
							"Connected to " + mConnectedDeviceName,
							Toast.LENGTH_SHORT).show();
					break;
				case MESSAGE_TOAST:
					Toast.makeText(getApplicationContext(),
							msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
							.show();
					break;
				}
			}
		};

		public void onActivityResult(int requestCode, int resultCode, Intent data) {
			if (D)
				Log.d(TAG, "onActivityResult " + resultCode);
			switch (requestCode) {
			case REQUEST_CONNECT_DEVICE:
				// When DeviceListActivity returns with a device to connect
				if (resultCode == Activity.RESULT_OK) {
					connectDevice(data);
				}
				break;
			case REQUEST_ENABLE_BT:
				// When the request to enable Bluetooth returns
				if (resultCode == Activity.RESULT_OK) {
					// Bluetooth is now enabled, so set up a chat session
					setupChat();
				} else {
					// User did not enable Bluetooth or an error occurred
					Log.d(TAG, "BT not enabled");
					Toast.makeText(this, R.string.bt_not_enabled_leaving,
							Toast.LENGTH_SHORT).show();
					finish();
				}
			}
		}
		
		private void connectDevice(Intent data) {
			// Get the device MAC address
			String address = data.getExtras().getString(
					DeviceListActivity.EXTRA_DEVICE_ADDRESS);
			// Get the BluetoothDevice object
			BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
			// Attempt to connect to the device
			mChatService.connect(device);
		}

		@Override
		public boolean onCreateOptionsMenu(Menu menu) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.option_menu, menu);
			return true;
		}

		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
			Intent serverIntent = null;
			switch (item.getItemId()) {
			case R.id.connect_scan:
				// Launch the DeviceListActivity to see devices and do scan
				serverIntent = new Intent(this, DeviceListActivity.class);
				startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
				return true;
			case R.id.discoverable:
				// Ensure this device is discoverable by others
				ensureDiscoverable();
				return true;
			}
			return false;
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
    	
    	// Contains the index of the edge of each occurrence of the second tone in each captured tone pair
    	selfToneEdges.clear();
    	otherToneEdges.clear();
    	
    	List<Integer> zeroCrossings = new ArrayList<Integer>();

		for (int i = 1; i < grandBuffer.size(); i++) {
			
			// Give up looking for the selfTones if we're more than halfway through the buffer
			if(i > grandBuffer.size() / 2 && expectedSpacing_1 == expectedSelfSpacing){
				expectedSpacing_1 = expectedZerosSpacing(sampleRate, OTHER_TONE_FREQUENCY);
				expectedSpacing_2 = expectedZerosSpacing(sampleRate, OTHER_TONE_FREQUENCY_2);
				Log.d(TAG, "Change expectations");
			}
						
        	if(((grandBuffer.get(i) < 0 && grandBuffer.get(i-1) > 0) || (grandBuffer.get(i) > 0 && grandBuffer.get(i-1) < 0))) {
        		zeroCrossings.add(i);
        		Log.d(TAG, "Found a zero at index " + i);
        		if(listeningFor == 1) {
        			//Log.d(TAG, "Listening for 1");
        			if(((i-i_lastZero) >= (expectedSpacing_1 - 1)) && 
        					((i-i_lastZero) <= (expectedSpacing_1 + 1))) {
        				conseqCount = conseqCount < CONSEQ_COUNT_THRESHOLD ? conseqCount + 1 : conseqCount;
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
        			//Log.d(TAG, "Listening for 2");
        			if(((i-i_lastZero) >= (expectedSpacing_2 - 1)) && 
        					((i-i_lastZero) <= (expectedSpacing_2 + 1))) {
        				conseqCount = conseqCount < CONSEQ_COUNT_THRESHOLD ? conseqCount + 1 : conseqCount;
        			} else {
        				conseqCount = 0;
        			}
        			if(conseqCount >= CONSEQ_COUNT_THRESHOLD) {
        				for(int j = 1; j < zeroCrossings.size() * 2 /numTones; j++) {
        					if((zeroCrossings.get(zeroCrossings.size() - j) 
        					  - zeroCrossings.get(zeroCrossings.size() - j - 1)) > 3*expectedSpacing_2/2) {
        						if(otherToneEdges.size() < numTones/2) {
                					otherToneEdges.add(i);
                					Log.d(TAG, "Add to other");
                					if(otherToneEdges.size() >= numTones/2) {
                						expectedSpacing_1 = expectedZerosSpacing(sampleRate, OTHER_TONE_FREQUENCY);
                    					expectedSpacing_2 = expectedZerosSpacing(sampleRate, OTHER_TONE_FREQUENCY_2);
                    					Log.d(TAG, "Change expectations");
                					}
                					break;
                				} else if(otherToneEdges.size() >= numTones/2 && selfToneEdges.size() < numTones/2) {
                					selfToneEdges.add(i);
                					Log.d(TAG, "Add to self");
                					if(selfToneEdges.size() >= numTones/2) {
                						Log.d(TAG, "break");
                					}
                					break;
                				}
        					}
        				}
        				conseqCount = 0;
        				listeningFor = 1;
        			}
        		}
        		i_lastZero = i;
        	}
        }
		
		List<Integer> deltas = new ArrayList<Integer>();
    	// Populate deltas list with index differences
    	for(int k = 0; k < Math.min(selfToneEdges.size(),  otherToneEdges.size()); k++) {
    		deltas.add(Math.abs(selfToneEdges.get(k) - otherToneEdges.get(k)));
    	}
    	
    	// Compute differences between these indices to remove outliers
    	Collections.sort(deltas);
    	
    	finalResult = (double) deltas.get(deltas.size() / 2); // Default middle of array
    	int smallestDeltaDifference = 5000; // Arbitrary high value
    	for(int m = 1; m < deltas.size(); m++) {
    		if(Math.abs(deltas.get(m) - deltas.get(m-1)) < smallestDeltaDifference) {
    			smallestDeltaDifference = Math.abs(deltas.get(m) - deltas.get(m-1));
    			finalResult = (double)(deltas.get(m) + deltas.get(m-1))/ 2;
    		}
    		// Exit this loop if two deltas agree nicely
    		if(Math.abs(deltas.get(m) - deltas.get(m-1)) <= 2) {
    			break;
    		} 
    	}
    	if(Math.abs(deltas.get(0) - deltas.get(deltas.size() - 1)) < smallestDeltaDifference) {
			smallestDeltaDifference = Math.abs(deltas.get(0) - deltas.get(deltas.size() - 1));
			finalResult = (double)(deltas.get(0) + deltas.get(deltas.size() - 1))/ 2;
		}
    	
		mSelfToneIndexText.setText("Self tone indices:");
		mOtherToneIndexText.setText("Other tone indices:");
		mIndexDiffText.setText("Index diffs:");
		//if(selfToneEdges.size() >= numTones / 2 && otherToneEdges.size() >= numTones / 2) {
		for(int j = 0; j < Math.min(selfToneEdges.size(), otherToneEdges.size()); j++) {
			mSelfToneIndexText.setText(mSelfToneIndexText.getText() + " " + String.valueOf(selfToneEdges.get(j)));
			mOtherToneIndexText.setText(mOtherToneIndexText.getText() + " " + String.valueOf(otherToneEdges.get(j)));
			mIndexDiffText.setText(mIndexDiffText.getText() + " " + String.valueOf(otherToneEdges.get(j) - selfToneEdges.get(j)));
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
    	    	FileWriter writer = new FileWriter(file);
    	    	writeCsvHeader(writer, "Self edges", "Other edges");
    	    	for(int i = 0; i < Math.min(selfEdges.size(), otherEdges.size()); i++) {
    	    		writeCsvHeader(writer, String.valueOf(selfEdges.get(i)), String.valueOf(otherEdges.get(i)));
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
    private void createBufferFile(List<?> buffer, String fileName) {
    	Calendar c = Calendar.getInstance(); 
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        dateFormat.setTimeZone(c.getTimeZone());
        timeFormat.setTimeZone(c.getTimeZone());
    	String FILE_NAME = timeFormat.format(c.getTime())+"_" + fileName + ".csv";
    	if (isExternalStorageWritable()) {
    		File root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS); 
    		File dir = new File (root.getAbsolutePath() + "/Thesis/" + dateFormat.format(c.getTime()));
    	    if (!dir.exists()) {
                dir.mkdirs();
            }
    	    //Toast.makeText(this, dir.toString(), Toast.LENGTH_SHORT).show();
    	    File file = new File(dir.getAbsolutePath(), FILE_NAME);
    	    try {
    	    	FileWriter writer = new FileWriter(file);
	            
    	    	// Write contents of buffer in
	            for(int i = 0; i < buffer.size(); i++) {
	            	String line = buffer.get(i).toString() + "\n";
	          	  	writer.write(line);
	            }
	            
	            writer.flush();
	            writer.close(); 
	            Log.d("FILEIO", "FINISHED WRITING");
	            Toast.makeText(this, fileName + " written to file.", Toast.LENGTH_SHORT).show();
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

    private void writeCsvHeader(FileWriter writer, String h1, String h2) throws IOException {
    	// Writes a space to avoid messin the first column
    	   String line = String.format(" ,%s,%s\n", h1,h2);
    	   writer.write(line);
    	 }

	private void writeCsvData(FileWriter writer, long selfTime, long totalTime) throws IOException {  
	  String line = String.format("%d,%d\n", selfTime, totalTime);
	  writer.write(line);
	}
}