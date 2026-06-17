package com.paymentswitch.mapper;

import com.paymentswitch.mapper.dto.MappingDtos.OutboundMapping;
import com.paymentswitch.mapper.dto.MappingDtos.TransformerMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AutomatedLineageService {

    private static final Logger log = LoggerFactory.getLogger(AutomatedLineageService.class);
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public List<TransformerMapping> extractInboundMappings() {
        List<TransformerMapping> mappings = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources("classpath*:**/*SpdhTransformerGeneric.java");
            if (resources.length == 0) {
                log.warn("Automated Inbound Dialect file not found in resources!");
                return mappings;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resources[0].getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                Pattern pattern = Pattern.compile("BaseSpdhTag\\.([a-zA-Z0-9_]+)");

                while ((line = reader.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        String spdhTag = matcher.group(1);
                        String inferredCtrxPath = inferInboundCtrxPath(line, spdhTag);
                        if (inferredCtrxPath != null) {
                            // isAuto flag set to TRUE
                            mappings.add(new TransformerMapping(spdhTag, inferredCtrxPath, "AUTO_SPDH_GENERIC", true));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse inbound dialect source code", e);
        }
        return mappings;
    }

    public List<OutboundMapping> extractOutboundMappings() {
        List<OutboundMapping> mappings = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources("classpath*:**/*BicIsoIssMessageTransformerGeneric.java");
            if (resources.length == 0) {
                log.warn("Automated Outbound Dialect file not found in resources!");
                return mappings;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resources[0].getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                Pattern pattern = Pattern.compile("BicIsoIssKeys\\.(f[0-9]+_[a-zA-Z0-9_]+)");

                while ((line = reader.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        String isoField = matcher.group(1);
                        String inferredCtrxPath = inferOutboundCtrxPath(line, isoField);
                        if (inferredCtrxPath != null) {
                            // isAuto flag set to TRUE
                            mappings.add(new OutboundMapping(isoField, inferredCtrxPath, "AUTO_BIC_ISO_GENERIC", true));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse outbound dialect source code", e);
        }
        return mappings;
    }

    private String inferInboundCtrxPath(String codeLine, String tag) {
        String lowerLine = codeLine.toLowerCase();
        if (lowerLine.contains("track1")) return "cardholder/track1";
        if (lowerLine.contains("track2")) return "cardholder/track2";
        if (lowerLine.contains("altaccountnumber")) return "cardholder/altAccountNumber";
        if (lowerLine.contains("pan")) return "cardholder/pan";
        if (lowerLine.contains("amount1")) return "amounts/primary/value";
        if (lowerLine.contains("currencycode")) return "amounts/primary/currency";
        if (lowerLine.contains("approvalcode")) return "procResult/approvalCode";
        if (lowerLine.contains("posconditioncode")) return "posInfo/posConditionCode";
        if (lowerLine.contains("posentrymode")) return "posInfo/posEntryMode";
        if (lowerLine.contains("draftcaptureflag")) return "processing/indicators/draftCaptureFlag";
        if (lowerLine.contains("receiptcode")) return "processing/trnIds/receipt";
        return null;
    }

    private String inferOutboundCtrxPath(String codeLine, String field) {
        if (field.contains("processingCode")) return "processing/procType";
        if (field.contains("amount")) return "amounts/primary/value";
        if (field.contains("dateTimeTransmission")) return "trnDates/transmissionDttm";
        if (field.contains("stan")) return "processing/trnIds/dstStan";
        if (field.contains("timeCreate")) return "trnDates/localDttm";
        if (field.contains("dateCreate")) return "trnDates/localDttm";
        if (field.contains("cardExpire")) return "cardholder/dateExpire";
        if (field.contains("posEntryMode")) return "posInfo/posEntryMode";
        if (field.contains("posConditionCode")) return "posInfo/posConditionCode";
        if (field.contains("track2")) return "cardholder/track2";
        if (field.contains("approvalCode")) return "procResult/approvalCode";
        if (field.contains("responseCode")) return "procResult/responseCode";
        if (field.contains("track1")) return "cardholder/track1";
        if (field.contains("currency")) return "amounts/primary/currency";
        return null;
    }
}