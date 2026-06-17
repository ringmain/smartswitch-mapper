package com.paymentswitch.mapper.dto;

@SuppressWarnings("unused")
public class MappingDtos {

    public record TransformerMapping(String fieldName, String ctrxPath, String channelDialect, Boolean isAuto) {}

    public record OutboundMapping(String fieldName, String ctrxPath, String channelDialect, Boolean isAuto) {}
}