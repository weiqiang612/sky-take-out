package com.weiqiang.skyai.customer.intent_recognition.service;

import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionRequest;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.intent_recognition.service.CustomerIntentRecognitionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerIntentRecognitionServiceTests {

    @Test
    void recognizeKeepsTrackDeliveryAndEntities() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CustomerIntentRecognitionTestConfiguration.class)) {
            MutableCustomerIntentRecognitionClientStub client = context.getBean(MutableCustomerIntentRecognitionClientStub.class);
            client.setResult(new IntentRecognitionResult(
                    IntentType.TRACK_DELIVERY,
                    ConfidenceLevel.HIGH,
                    Map.of("order_id", "123"),
                    List.of(IntentType.TRACK_DELIVERY, IntentType.ORDER_STATUS),
                    null,
                    false,
                    null
            ));

            CustomerIntentRecognitionService service = context.getBean(CustomerIntentRecognitionService.class);
            IntentRecognitionResult result = service.recognize(new IntentRecognitionRequest("我的订单 123 到哪了", List.of("用户上一句：我想查单")));

            assertEquals(IntentType.TRACK_DELIVERY, result.intent());
            assertEquals(ConfidenceLevel.HIGH, result.confidence());
            assertEquals(Map.of("order_id", "123"), result.entities());
            assertEquals(List.of(IntentType.TRACK_DELIVERY, IntentType.ORDER_STATUS), result.possibleIntents());
            assertFalse(result.requiresHumanConfirmation());
            assertNull(result.clarificationQuestion());
        }
    }

    @Test
    void recognizeForcesHumanConfirmationForHighRiskIntent() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CustomerIntentRecognitionTestConfiguration.class)) {
            MutableCustomerIntentRecognitionClientStub client = context.getBean(MutableCustomerIntentRecognitionClientStub.class);
            client.setResult(new IntentRecognitionResult(
                    IntentType.CANCEL_ORDER,
                    ConfidenceLevel.HIGH,
                    Map.of("order_id", "123"),
                    List.of(),
                    null,
                    false,
                    null
            ));

            CustomerIntentRecognitionService service = context.getBean(CustomerIntentRecognitionService.class);
            IntentRecognitionResult result = service.recognize(new IntentRecognitionRequest("我要取消订单 123", List.of()));

            assertTrue(result.requiresHumanConfirmation());
            assertEquals("该意图属于高风险操作，需要人工确认后再继续处理。", result.humanConfirmationReason());
        }
    }

    @Test
    void recognizeBuildsClarificationQuestionForLowConfidenceFallback() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CustomerIntentRecognitionTestConfiguration.class)) {
            MutableCustomerIntentRecognitionClientStub client = context.getBean(MutableCustomerIntentRecognitionClientStub.class);
            client.setResult(new IntentRecognitionResult(
                    IntentType.OTHER,
                    ConfidenceLevel.LOW,
                    Map.of(),
                    List.of(IntentType.REQUEST_REFUND, IntentType.REPORT_MISSING_ITEM),
                    null,
                    false,
                    null
            ));

            CustomerIntentRecognitionService service = context.getBean(CustomerIntentRecognitionService.class);
            IntentRecognitionResult result = service.recognize(new IntentRecognitionRequest("能不能处理一下", List.of("用户：订单有点问题")));

            assertEquals(ConfidenceLevel.LOW, result.confidence());
            assertEquals("可以补充一下你的具体诉求吗，例如订单号、商品或想处理的事项？", result.clarificationQuestion());
            assertEquals(List.of(IntentType.REQUEST_REFUND, IntentType.REPORT_MISSING_ITEM), result.possibleIntents());
        }
    }

    @Test
    void recognizeBuildsClarificationQuestionForOtherIntent() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CustomerIntentRecognitionTestConfiguration.class)) {
            MutableCustomerIntentRecognitionClientStub client = context.getBean(MutableCustomerIntentRecognitionClientStub.class);
            client.setResult(new IntentRecognitionResult(
                    IntentType.OTHER,
                    ConfidenceLevel.HIGH,
                    Map.of(),
                    List.of(IntentType.OTHER),
                    null,
                    false,
                    null
            ));

            CustomerIntentRecognitionService service = context.getBean(CustomerIntentRecognitionService.class);
            IntentRecognitionResult result = service.recognize(new IntentRecognitionRequest("随便看看", List.of()));

            assertEquals(IntentType.OTHER, result.intent());
            assertEquals("可以补充一下你的具体诉求吗，例如订单号、商品或想处理的事项？", result.clarificationQuestion());
        }
    }

    @Test
    void recognizeReturnsFallbackWhenClientReturnsNull() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CustomerIntentRecognitionTestConfiguration.class)) {
            MutableCustomerIntentRecognitionClientStub client = context.getBean(MutableCustomerIntentRecognitionClientStub.class);
            client.setResult(null);

            CustomerIntentRecognitionService service = context.getBean(CustomerIntentRecognitionService.class);
            IntentRecognitionResult result = service.recognize(new IntentRecognitionRequest("我要退款还是重送都行", List.of()));

            assertEquals(IntentType.OTHER, result.intent());
            assertEquals(ConfidenceLevel.LOW, result.confidence());
            assertTrue(result.entities().isEmpty());
            assertTrue(result.possibleIntents().isEmpty());
            assertFalse(result.requiresHumanConfirmation());
            assertEquals("可以补充一下你的具体诉求吗，例如订单号、商品或想处理的事项？", result.clarificationQuestion());
        }
    }

    @Test
    void recognizeDoesNotForceHumanConfirmationForHighRiskIntentWithoutOrderId() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CustomerIntentRecognitionTestConfiguration.class)) {
            MutableCustomerIntentRecognitionClientStub client = context.getBean(MutableCustomerIntentRecognitionClientStub.class);
            client.setResult(new IntentRecognitionResult(
                    IntentType.CANCEL_ORDER,
                    ConfidenceLevel.HIGH,
                    Map.of(),
                    List.of(),
                    null,
                    false,
                    null
            ));

            CustomerIntentRecognitionService service = context.getBean(CustomerIntentRecognitionService.class);
            IntentRecognitionResult result = service.recognize(new IntentRecognitionRequest("我要取消订单", List.of()));

            assertFalse(result.requiresHumanConfirmation());
        }
    }
}
