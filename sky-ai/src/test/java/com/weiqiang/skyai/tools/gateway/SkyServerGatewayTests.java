package com.weiqiang.skyai.tools.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SkyServerGatewayTests {

    @Test
    void setDefaultAddressReturnsSnapshotAfterUpdate() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        SkyServerGateway gateway = new SkyServerGateway(restClientBuilder);
        ReflectionTestUtils.setField(gateway, "baseUrl", "https://sky.test");

        server.expect(requestTo("https://sky.test/ai/customer/addresses/default"))
                .andExpect(method(PUT))
                .andExpect(content().json("{\"id\":7}"))
                .andRespond(withSuccess("{\"code\":1,\"data\":null}", APPLICATION_JSON));
        server.expect(requestTo("https://sky.test/ai/customer/addresses/default"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"code":1,"data":{"id":7,"consignee":"张三","phone":"13800000000","detail":"朝阳区建国路1号","label":"家","isDefault":1}}
                        """, APPLICATION_JSON));

        String result = gateway.setDefaultAddress("u1", 7L);

        assertEquals("{\"id\":7,\"consignee\":\"张三\",\"phone\":\"13800000000\",\"detail\":\"朝阳区建国路1号\",\"label\":\"家\",\"isDefault\":1}", result);
        server.verify();
    }
}
