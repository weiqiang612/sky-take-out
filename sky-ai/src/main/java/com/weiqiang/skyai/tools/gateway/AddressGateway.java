package com.weiqiang.skyai.tools.gateway;

public interface AddressGateway {

    String listAddresses(String userId);

    String getDefaultAddress(String userId);

    String setDefaultAddress(String userId, Long addressId);

    String updateAddress(String userId, Long addressId, String consignee, String phone, String detail);
}
