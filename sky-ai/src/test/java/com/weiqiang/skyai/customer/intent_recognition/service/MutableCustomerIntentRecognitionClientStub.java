package com.weiqiang.skyai.customer.intent_recognition.service;

import com.weiqiang.skyai.intent_recognition.client.CustomerIntentRecognitionClient;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionRequest;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;

class MutableCustomerIntentRecognitionClientStub implements CustomerIntentRecognitionClient {

    private IntentRecognitionResult result;

    @Override
    public IntentRecognitionResult recognize(IntentRecognitionRequest request) {
        return result;
    }

    void setResult(IntentRecognitionResult result) {
        this.result = result;
    }
}
