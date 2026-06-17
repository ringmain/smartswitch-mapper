package cz.monetplus.smartswitch.spdh.tags;

import cz.monetplus.spdh.commons.SpdhTag;

public enum QrPaymentV1SpdhTag implements SpdhTag {

    AvailablePaymentMethod("8d"),
    SubPayMethod ("8e"),
    Url("8f"),
    Status("8g"),
    TimeoutDelay("8h"),

    ;

    private final String tag;

    private QrPaymentV1SpdhTag(String tag) {
        this.tag = tag;
    }

    @Override
    public String getTag() {
        return tag;
    }
}
