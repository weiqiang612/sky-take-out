package com.weiqiang.skyai.intent_recognition.client;

import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionRequest;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;

public interface CustomerIntentRecognitionClient {

    IntentRecognitionResult recognize(IntentRecognitionRequest request);
}
