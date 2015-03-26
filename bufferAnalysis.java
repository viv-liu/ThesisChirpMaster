private void bufferAnalysis() {
		int expectedSelfSpacing = expectedZerosSpacing(sampleRate, SELF_TONE_FREQUENCY);
		int expectedSelfSpacing_2 = expectedZerosSpacing(sampleRate, SELF_TONE_FREQUENCY_2);
    	
    	int expectedSpacing_1 = expectedSelfSpacing;
    	int expectedSpacing_2 = expectedSelfSpacing_2;
    	String isListeningFor = "Self";
    	
		final int CONSEQ_COUNT_THRESHOLD = 10;
		int i_lastZero = 0;
    	int conseqCount = 0;
    	
    	// A truncated version of genTone including a single period of two tones is used to detect edges in convolution, with convolution result ignoring jittery beginning and end of tone segments
    	//List<Double> selfGenTone = createOutputToneArrayAmplitudeVarying(SELF_TONE_FREQUENCY, "selfTone");
    	//List<Double> truncGenTone = selfGenTone.subList(995, 2995);
    	//List<Double> otherGenTone = createOutputToneArrayAmplitudeVarying(OTHER_TONE_FREQUENCY, "otherTone");
    	//List<Double> truncOtherGenTone = otherGenTone.subList(995, 2995);
    	List<Double> selfConvolveResult = new ArrayList<Double>(); 
    	List<Double> otherConvolveResult = new ArrayList<Double>();
    	// Contains the index of the edge of each occurence of the second tone in each captured tone pair
    	selfToneEdges.clear();
    	otherToneEdges.clear();
    	
    	/*double selfProductSum = 0;
    	double otherProductSum = 0;
    	// Perform convolution
    	for (int i = 0; i < grandBuffer.size(); i++) {
    		for(int j = 0; j < Math.min(truncGenTone.size(), truncOtherGenTone.size()); j++) { //trunc arrays should have same sizes
    			// If convolving near the end of grandBuffer, add zeros to compensate for lack of data
    			if(i+j >= grandBuffer.size()) {
    				selfProductSum += 0 * truncGenTone.get(j); // = 0
    				otherProductSum += 0 * truncOtherGenTone.get(j);
    			} else {
    				selfProductSum += grandBuffer.get(i+j) * truncGenTone.get(j);
    				otherProductSum += grandBuffer.get(i+j) * truncOtherGenTone.get(j);
    			}
    		}
    		// Normalize the result to reduce size of stored value
    		selfConvolveResult.add(selfProductSum/truncGenTone.size());
    		selfProductSum = 0;
    		otherConvolveResult.add(otherProductSum/truncOtherGenTone.size());
    		otherProductSum = 0;
    	}
    	createBufferFile(selfConvolveResult, "selfConvolveResult");
    	createBufferFile(otherConvolveResult, "otherConvolveResult");*/
    	List<Short> peaks = new ArrayList<Short>();
    	List<Integer> indices = new ArrayList<Integer>();
    	short maxSoFar = 0;
    	int maxSoFarIndex = 0;
		for (int i = 1; i < grandBuffer.size(); i++) {
			
			// Give up looking for the selfTones if we're more than halfway through the buffer
			if(i > grandBuffer.size() / 2 && isListeningFor.equals("Self")){
				isListeningFor = "Other";
				expectedSpacing_1 = expectedZerosSpacing(sampleRate, OTHER_TONE_FREQUENCY);
				expectedSpacing_2 = expectedZerosSpacing(sampleRate, OTHER_TONE_FREQUENCY_2);
				Log.d(TAG, "Change expectations");
			}
        	if((grandBuffer.get(i) < 0 && grandBuffer.get(i-1) > 0) || (grandBuffer.get(i) > 0 && grandBuffer.get(i-1) < 0)) {
        		Log.d(TAG, "Found a zero at index " + i);
        		//if(listeningFor == 1) {
        			//Log.d(TAG, "Listening for 1");
        			if(((i-i_lastZero) >= (expectedSpacing_1 - 1)) && 
        					((i-i_lastZero) <= (expectedSpacing_1 + 1))) {
        				conseqCount++;
        				//Log.d(TAG, "Good spacing " + String.valueOf(i));
        			} else if (((i-i_lastZero) >= (expectedSpacing_2 - 2)) && 
        					((i-i_lastZero) <= (expectedSpacing_2 + 2))){
        				if(conseqCount >= CONSEQ_COUNT_THRESHOLD) {
        					conseqCount = 0;
        					if(isListeningFor.equals("Self")) {
            					selfToneEdges.add(i);
            					Log.d(TAG, "Add to self at index " + i);
            					if(selfToneEdges.size() >= numTones/2) {
            						isListeningFor = "Other";
            						expectedSpacing_1 = expectedZerosSpacing(sampleRate, OTHER_TONE_FREQUENCY);
                					expectedSpacing_2 = expectedZerosSpacing(sampleRate, OTHER_TONE_FREQUENCY_2);
                					Log.d(TAG, "Change expectations");
            					}
            					continue;
            				} 
        					if(isListeningFor.equals("Other")) {
            					otherToneEdges.add(i);
            					Log.d(TAG, "Add to other at index " + i);
            					if(otherToneEdges.size() >= numTones/2) {
            						Log.d(TAG, "break");
                					break;
            					}
            					continue;
            				}
        				}
        				//Log.d(TAG, "Bad spacing " + String.valueOf(i));
        			} else {
        				conseqCount -= 2;
        			}
        			/*if(conseqCount >= CONSEQ_COUNT_THRESHOLD) {
        				//Log.d(TAG, "Transitioning to 2");
        				//heard first tone
        				listeningFor = 2;
            			conseqCount = 0;*/
            	//	}
        			
        		/*} else if(listeningFor == 2) {
        			Log.d(TAG, "Listening for 2");
        			if(((i-i_lastZero) >= (expectedSpacing_2 - 2)) && 
        					((i-i_lastZero) <= (expectedSpacing_2 + 2))) {
        				conseqCount++;
        			} else {
        				conseqCount = 0;
        			}
        			if(conseqCount >= CONSEQ_COUNT_THRESHOLD) {
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
        		}*/
        		i_lastZero = i;
        	}
			// Keeps largest POSITIVE peak
        	/*if(grandBuffer.get(i) > maxSoFar) {
        		maxSoFar = grandBuffer.get(i);
        		maxSoFarIndex = i;
        	}
        	if((grandBuffer.get(i) < 0 && grandBuffer.get(i-1) > 0) || (grandBuffer.get(i) > 0 && grandBuffer.get(i-1) < 0)){
        		peaks.add(maxSoFar);
        		indices.add(maxSoFarIndex);
        		maxSoFar = 0;
        		if(listeningFor == 1) {
        			if(conseqCount < CONSEQ_COUNT_THRESHOLD) {
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
        			}
        		} else if(listeningFor == 2) {
        			int j;
        			double curAmpMean = 0;
        			double curVariance = 0;
        			double stdev = 0;
        			double curSpacingMean = 0;
        			// Find mean amplitude of most recent 10 peaks
        			for(j = peaks.size() - 2; j > peaks.size() - 4 && j >= 0; j--) {
        				curAmpMean += peaks.get(j);
        			}
        			curAmpMean /= (peaks.size() - j); // Divide by number of times the above for loop looped
        			// Find variance using mean
        			for(j = peaks.size() - 2; j > peaks.size() - 4 && j >= 0; j--) {
        				curVariance += (peaks.get(j) - curAmpMean) * (peaks.get(j) - curAmpMean);
        			}
        			curVariance /= (peaks.size() - j); // Divide by number of times the above for loop looped
        			// Find standard deviation using variance
        			stdev = Math.sqrt(curVariance);
        			
        			// Find mean spacing between most recent 10 peaks
        			for(j = indices.size() - 2; j > indices.size() - 4 && j >= 1; j--) {
        				curSpacingMean += indices.get(j) - indices.get(j-1);
        			}
        			curSpacingMean /= (indices.size() - j);
        			
        			if(Math.abs(indices.get(indices.size() - 1) - indices.get(indices.size() - 2)) < curSpacingMean - 3 || 
        					Math.abs(indices.get(indices.size() - 1) - indices.get(indices.size() - 2)) > curSpacingMean - 3) {
        				Log.d(TAG, "Peak spacing anomaly detected at index " + indices.get(indices.size() - 1) 
        						+ " amplitude " + peaks.get(peaks.size() - 1));
        				if(Math.abs(peaks.get(peaks.size() - 1) - curAmpMean) > stdev) {
        					Log.d(TAG, "Amplitude outside of stdev, further proof that this is a tone change.");
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
        			
        		}
        		i_lastZero = i;
        	}*/
        }
		mSelfToneIndexText.setText("Self tone indices:");
		mOtherToneIndexText.setText("Other tone indices:");
		mIndexDiffText.setText("Index diffs:");
		//if(selfToneEdges.size() >= numTones / 2 && otherToneEdges.size() >= numTones / 2) {
		for(int i = 0; i < Math.min(selfToneEdges.size(), otherToneEdges.size()); i++) {
			mSelfToneIndexText.setText(mSelfToneIndexText.getText() + " " + String.valueOf(selfToneEdges.get(i)));
			mOtherToneIndexText.setText(mOtherToneIndexText.getText() + " " + String.valueOf(otherToneEdges.get(i)));
			mIndexDiffText.setText(mIndexDiffText.getText() + " " + String.valueOf(otherToneEdges.get(i) - selfToneEdges.get(i)));
		}
	}