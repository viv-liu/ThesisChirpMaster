package com.example.playtonemaster;

import android.os.CountDownTimer;
import android.widget.TextView;

public class PausableCountdownTimer extends CountDownTimer {
	private boolean mIsRunning = false;
	private long mMillisUntilFinished;
	private TextView mTimeLeftText;
	
	public PausableCountdownTimer(long millisInFuture,
			long countDownInterval, TextView tv) {
		super(millisInFuture, countDownInterval);
		mTimeLeftText = tv;
		
	}

	@Override
	public void onTick(long millisUntilFinished) {
		mTimeLeftText.setText(String.valueOf(millisUntilFinished/1000.0) + " s");
		mIsRunning = true;
		mMillisUntilFinished = millisUntilFinished;
		//playSound();
	}

	@Override
	public void onFinish() {
		mIsRunning = false;
		mMillisUntilFinished = 0;
		/*recordTask.cancel(false);
		mPlayButton.setText("Save records");*/
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
