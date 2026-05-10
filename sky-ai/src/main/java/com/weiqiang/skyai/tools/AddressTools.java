package com.weiqiang.skyai.tools;

import com.weiqiang.skyai.tools.gateway.AddressGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AddressTools {

    private final AddressGateway addressGateway;

    @Tool(description = "List all saved delivery addresses for the current user.")
    public String listAddresses(ToolContext context) {
        return addressGateway.listAddresses(ToolUser.userId(context));
    }

    @Tool(description = "Get the current user's default delivery address.")
    public String getDefaultAddress(ToolContext context) {
        return addressGateway.getDefaultAddress(ToolUser.userId(context));
    }

    @Tool(description = "Set one saved address as the current user's default address after confirmation.")
    public String setDefaultAddress(@ToolParam(description = "Address id") Long addressId, ToolContext context) {
        return addressGateway.setDefaultAddress(ToolUser.userId(context), addressId);
    }

    @Tool(description = "Update a saved delivery address after the user confirms the replacement details.")
    public String updateAddress(@ToolParam(description = "Address id") Long addressId,
                                @ToolParam(description = "Consignee name") String consignee,
                                @ToolParam(description = "Phone number") String phone,
                                @ToolParam(description = "Detailed delivery address") String detail,
                                ToolContext context) {
        return addressGateway.updateAddress(ToolUser.userId(context), addressId, consignee, phone, detail);
    }
}
