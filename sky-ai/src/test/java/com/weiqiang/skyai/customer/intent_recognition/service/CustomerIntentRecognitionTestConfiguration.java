package com.weiqiang.skyai.customer.intent_recognition.service;

import com.weiqiang.skyai.intent_recognition.service.CustomerIntentRecognitionService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import(CustomerIntentRecognitionService.class)
class CustomerIntentRecognitionTestConfiguration {

    @Bean
    MutableCustomerIntentRecognitionClientStub customerIntentRecognitionClient() {
        return new MutableCustomerIntentRecognitionClientStub();
    }
}
