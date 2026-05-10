package com.weiqiang.skyai.customer.intent_recognition.service;

import com.weiqiang.skyai.intent_recognition.service.CustomerIntentRecognitionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(CustomerIntentRecognitionService.class)
class CustomerIntentRecognitionTestConfiguration {

    @Bean
    MutableCustomerIntentRecognitionClientStub customerIntentRecognitionClient() {
        return new MutableCustomerIntentRecognitionClientStub();
    }
}
