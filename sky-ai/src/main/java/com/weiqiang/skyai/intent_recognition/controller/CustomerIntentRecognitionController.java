package com.weiqiang.skyai.intent_recognition.controller;

import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionRequest;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.service.CustomerIntentRecognitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customer/intent")
@RequiredArgsConstructor
public class CustomerIntentRecognitionController {

    private final CustomerIntentRecognitionService customerIntentRecognitionService;

    @PostMapping("/recognize")
    public IntentRecognitionResult recognize(@RequestBody IntentRecognitionRequest request) {
        return customerIntentRecognitionService.recognize(request);
    }
}
