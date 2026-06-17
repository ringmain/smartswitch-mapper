package com.paymentswitch.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JsonModelParser {

    private static final Logger log = LoggerFactory.getLogger(JsonModelParser.class);
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final File schemaFile;
    private final File metadataFile;
    private final File dataDir;

    // High-performance Thread-Safe Memory Caches
    private final Map<String, Map<String, String>> metadataCache = new ConcurrentHashMap<>();
    private final Map<String, String> spdhInMemoryDescriptions = new ConcurrentHashMap<>();
    private final Map<String, String> outChannelInMemoryDescriptions = new ConcurrentHashMap<>();

    // Inject file paths dynamically with defaults
    public JsonModelParser(
            @Value("${mapper.file.schema:data/trxJson.json}") String schemaPath,
            @Value("${mapper.file.metadata:data/ctrx_metadata.json}") String metadataPath,
            @Value("${mapper.dir.data:data}") String dataDirPath) {
        this.schemaFile = new File(schemaPath);
        this.metadataFile = new File(metadataPath);
        this.dataDir = new File(dataDirPath);
    }

    // Runs automatically on Spring Boot Startup
    @PostConstruct
    public void initCache() {
        if (metadataFile.exists() && metadataFile.length() > 0) {
            try {
                Map<String, Map<String, String>> fileData = mapper.readValue(metadataFile, new TypeReference<>() {});
                metadataCache.putAll(fileData);
                log.info("Successfully loaded {} CTRX metadata entries into memory cache.", metadataCache.size());
            } catch (IOException e) {
                log.error("Failed to load metadata cache into memory on startup", e);
            }
        }
    }

    public Map<String, Object> loadCombinedSchemaAndMetadata() throws Exception {
        if (!schemaFile.exists()) {
            throw new FileNotFoundException("Base schema file not found at: " + schemaFile.getAbsolutePath());
        }
        Map<String, Object> rawJson = mapper.readValue(schemaFile, Map.class);

        List<DBeaverRow> combinedDBeaverRows = new ArrayList<>();
        if (dataDir.exists() && dataDir.isDirectory()) {
            File[] files = dataDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    if (fileName.contains("_table_dump")) {
                        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
                        String extractedTableName = baseName.replace("_table_dump", "").trim();
                        if (!extractedTableName.isEmpty()) {
                            combinedDBeaverRows.addAll(parseDBeaverFile(file, extractedTableName));
                        }
                    }
                }
            }
        }

        Map<String, Object> finalTree = new LinkedHashMap<>();
        // Inject the memory cache directly instead of hitting the disk!
        buildSchemaTree(rawJson, finalTree, metadataCache, combinedDBeaverRows, "");
        return finalTree;
    }

    private List<DBeaverRow> parseDBeaverFile(File file, String tableName) {
        List<DBeaverRow> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.toLowerCase().startsWith("column name")) continue;

                String[] tokens = line.split("\\s+");
                if (tokens.length < 3) continue;

                String columnName = tokens[0].trim();
                String dataTypeRaw = tokens[2].trim();

                String dataType = dataTypeRaw;
                String length = "";
                if (dataTypeRaw.contains("(")) {
                    dataType = dataTypeRaw.substring(0, dataTypeRaw.indexOf("("));
                    length = dataTypeRaw.substring(dataTypeRaw.indexOf("(") + 1, dataTypeRaw.indexOf(")"));
                }

                dataType = dataType.toUpperCase();
                if (dataType.contains("CHAR")) dataType = dataType.contains("VAR") ? "VARCHAR" : "CHAR";
                if (dataType.contains("INT") || dataType.contains("BIGINT")) {
                    dataType = dataTypeRaw.toLowerCase().contains("bigint") ? "BIGINT" : "INT";
                }
                if (dataType.contains("NUMBER") || dataType.contains("DECIMAL") || dataType.contains("NUMERIC")) dataType = "NUMERIC";
                if (dataType.contains("DATE") || dataType.contains("TIME")) dataType = "TIMESTAMP";

                String lastToken = tokens[tokens.length - 1];
                String jsonPathHint = null;
                if (lastToken.contains(".") && !lastToken.startsWith("'") && !lastToken.startsWith("[")) {
                    jsonPathHint = lastToken.trim();
                }

                DBeaverRow row = new DBeaverRow();
                row.columnName = columnName;
                row.dataType = dataType;
                row.length = length;
                row.jsonPathHint = jsonPathHint;
                row.tableName = tableName;
                rows.add(row);
            }
        } catch (Exception e) {
            log.error("Failed to read schema DBeaver file [{}]", file.getName(), e);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private void buildSchemaTree(Map<String, Object> source, Map<String, Object> target,
                                 Map<String, Map<String, String>> customMetadata,
                                 List<DBeaverRow> dbeaverRows, String prefix) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String currentPath = prefix.isEmpty() ? key : prefix + "/" + key;

            if (value instanceof Map) {
                Map<String, Object> subTarget = new LinkedHashMap<>();
                target.put(key, subTarget);
                buildSchemaTree((Map<String, Object>) value, subTarget, customMetadata, dbeaverRows, currentPath);
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                if (!list.isEmpty() && list.get(0) instanceof Map) {
                    Map<String, Object> subTarget = new LinkedHashMap<>();
                    target.put(key, subTarget);
                    buildSchemaTree((Map<String, Object>) list.get(0), subTarget, customMetadata, dbeaverRows, currentPath);
                } else {
                    target.put(key, resolveLeafMetadata(currentPath, value, customMetadata, dbeaverRows));
                }
            } else {
                target.put(key, resolveLeafMetadata(currentPath, value, customMetadata, dbeaverRows));
            }
        }
    }

    private Map<String, String> resolveLeafMetadata(String path, Object value,
                                                    Map<String, Map<String, String>> customMetadata,
                                                    List<DBeaverRow> dbeaverRows) {
        String keyName = path.substring(path.lastIndexOf("/") + 1);
        String normalizedKey = keyName.toLowerCase().replace("_", "");
        String lowerPath = path.toLowerCase();

        String dataType = "VARCHAR";
        String length = "";
        String dbTable = "";
        String exampleValue = (value != null) ? value.toString() : "";
        String description = "";

        String lowercaseKey = keyName.toLowerCase();
        if (lowercaseKey.contains("id") || lowercaseKey.contains("stan") || lowercaseKey.contains("counter")) {
            dataType = (value instanceof Number && ((Number)value).longValue() > 2147483647) ? "BIGINT" : "INT";
        } else if (lowercaseKey.contains("value") || lowercaseKey.contains("amount") || lowercaseKey.contains("balance")) {
            dataType = "NUMERIC";
            length = "12";
        } else if (lowercaseKey.contains("dttm") || lowercaseKey.contains("date") || lowercaseKey.contains("time")) {
            dataType = "TIMESTAMP";
        } else if (value instanceof Boolean || lowercaseKey.contains("flag") || lowercaseKey.contains("indicator")) {
            dataType = "CHAR";
            length = "1";
        }

        DBeaverRow bestMatch = null;
        for (DBeaverRow row : dbeaverRows) {
            if (row.jsonPathHint != null) {
                String normHint = row.jsonPathHint.toLowerCase().replace("procresut", "procresult").replace("amounts", "amount");
                String normPath = lowerPath.replace("procresut", "procresult").replace("amounts", "amount");

                String[] parts = normHint.split("\\.");
                boolean sequentialMatch = true;
                int searchAnchorIndex = -1;

                for (String part : parts) {
                    int foundIdx = normPath.indexOf(part, searchAnchorIndex + 1);
                    if (foundIdx == -1) {
                        sequentialMatch = false;
                        break;
                    }
                    searchAnchorIndex = foundIdx;
                }
                if (sequentialMatch) {
                    bestMatch = row;
                    break;
                }
            }
        }

        if (bestMatch == null) {
            for (DBeaverRow row : dbeaverRows) {
                String normCol = row.columnName.toLowerCase().replace("_", "");
                if (normCol.equals(normalizedKey)) {
                    bestMatch = row;
                    break;
                }
            }
        }

        if (bestMatch != null) {
            dataType = bestMatch.dataType;
            length = bestMatch.length.isEmpty() ? length : bestMatch.length;
            dbTable = bestMatch.tableName;
            description = "Auto-linked to table [" + bestMatch.tableName + "], column [" + bestMatch.columnName + "] via schema definitions.";
        }

        if (customMetadata.containsKey(path)) {
            Map<String, String> custom = customMetadata.get(path);
            if (custom.containsKey("dataType") && !custom.get("dataType").isEmpty()) dataType = custom.get("dataType");
            if (custom.containsKey("length") && !custom.get("length").isEmpty()) length = custom.get("length");
            if (custom.containsKey("dbTable") && !custom.get("dbTable").isEmpty()) dbTable = custom.get("dbTable");
            if (custom.containsKey("exampleValue") && !custom.get("exampleValue").isEmpty()) exampleValue = custom.get("exampleValue");
            if (custom.containsKey("description") && !custom.get("description").isEmpty()) description = custom.get("description");
        }

        Map<String, String> meta = new HashMap<>();
        meta.put("dataType", dataType);
        meta.put("length", length);
        meta.put("dbTable", dbTable);
        meta.put("exampleValue", exampleValue);
        meta.put("description", description);
        meta.put("isLeafMarker", "true");

        return meta;
    }

    public void saveAttributeMetadata(String path, Map<String, String> updatedMeta) throws Exception {
        Map<String, String> cleanMeta = new HashMap<>();
        cleanMeta.put("dataType", updatedMeta.getOrDefault("dataType", "VARCHAR"));
        cleanMeta.put("length", updatedMeta.getOrDefault("length", ""));
        cleanMeta.put("dbTable", updatedMeta.getOrDefault("dbTable", ""));
        cleanMeta.put("exampleValue", updatedMeta.getOrDefault("exampleValue", ""));
        cleanMeta.put("description", updatedMeta.getOrDefault("description", ""));

        // 1. Update memory instantly
        metadataCache.put(path, cleanMeta);

        // 2. Blindly flush the entire map to disk without an expensive pre-read operation
        mapper.writeValue(metadataFile, metadataCache);
    }

    public List<Map<String, String>> getSpdhTagsByDialect(String dialect) {
        List<Map<String, String>> tagList = new ArrayList<>();
        if ("QrPaymentV1SpdhTag".equalsIgnoreCase(dialect)) {
            tagList.add(createTagMap("AvailablePaymentMethod", "8d", "Available Payment Method options", false));
            tagList.add(createTagMap("SubPayMethod", "8e", "Sub Payment Method parameters", false));
            tagList.add(createTagMap("Url", "8f", "QR Payment Redirection Link URL", false));
            tagList.add(createTagMap("Status", "8g", "Dynamic Transaction Workflow Status", false));
            tagList.add(createTagMap("TimeoutDelay", "8h", "Timeout tracking delay specification", false));
        } else {
            tagList.add(createTagMap("Track1", "2", "Track1 card information data stream", false));
            tagList.add(createTagMap("IndustryData", "4", "Binary EMV chip metadata block (Base64)", false));
            tagList.add(createTagMap("POSEntryMode", "6E", "Point of Service Entry Mode code indicator", false));
            tagList.add(createTagMap("EmvRequestData", "6O", "Primary EMV terminal chip specification block", false));
            tagList.add(createTagMap("EmvRequestDataAdditional", "6P", "Supplementary EMV request configuration criteria", false));
            tagList.add(createTagMap("EmvSupplRequestCless", "6q", "Contactless EMV chip payload properties", false));
            tagList.add(createTagMap("EmvRequestAdditional2", "8q", "Deep variant EMV core processing arguments", false));
            tagList.add(createTagMap("ApprovalCode", "F", "System response authorization approval validation token", false));
            tagList.add(createTagMap("Amount1", "B", "Primary transaction currency ledger valuation amount", false));
            tagList.add(createTagMap("Amount2", "C", "Secondary additional ledger amount (Cashback/Adjustment)", false));
            tagList.add(createTagMap("CurrencyCode", "6I", "ISO numeric identifier code specifying active currency", false));
            tagList.add(createTagMap("amountTip", "9c", "Gratuity tipping processing amount", false));
            tagList.add(createTagMap("AsorsFoodData", "9G", "Meal voucher / Gastro specific domain data payload", false));
            tagList.add(createTagMap("Installment", "9b", "Installment loans consumer option specification", false));
            tagList.add(createTagMap("AdditionalPointOfServiceData", "60", "Point of Service context metrics data block", false));
            tagList.add(createTagMap("ManualCvd", "6B", "Card Verification Code digits input value", false));
            tagList.add(createTagMap("SoftPosData", "8s", "SoftPOS mobile hardware software metrics footprint", false));
            tagList.add(createTagMap("DccOffer", "9O", "Dynamic Currency Conversion offering quotation details", false));
            tagList.add(createTagMap("DccRequest", "9Q", "Terminal initial DCC verification lookup criteria", false));
            tagList.add(createTagMap("DccIdentifier", "9I", "Unique transaction reference logging DCC index", false));
            tagList.add(createTagMap("DccResult", "9R", "Consumer checkout decision selection status result", false));
            tagList.add(createTagMap("ProjectVersion", "9V", "Custom network layout protocol specification index", false));
            tagList.add(createTagMap("ProductData", "9p", "Fuel loyalty and commodity line item profile data", false));
            tagList.add(createTagMap("CommodityHeader", "9H", "Fuel item grouping array matrix meta-header", false));
            tagList.add(createTagMap("CommodityData", "9D", "Detailed nested array of specific fuel elements", false));
            tagList.add(createTagMap("POSConditionCode", "e", "Point of Service operational condition environment status", false));
            tagList.add(createTagMap("LanguageCode", "9U", "Preferred language localization response target layout", false));
            tagList.add(createTagMap("DraftCaptureFlag", "P", "Transaction draft capture method operational indicator", false));
            tagList.add(createTagMap("PartialAmountInd", "9P", "Approval threshold flexibility authorization indicator", false));
            tagList.add(createTagMap("ProcessorToken", "9d", "Token substitution registration reference flag", false));
            tagList.add(createTagMap("CustomerID", "N", "Customer messaging processing cancellation reason codes", false));
            tagList.add(createTagMap("VariableSymbol", "9S", "Localized payment routing reference transaction index", false));
            tagList.add(createTagMap("OriginalReceiptCode", "S", "Original lookup reference code linking parent items", false));
            tagList.add(createTagMap("InvoiceNumberOriginal", "T", "Invoice trace documentation index parameter", false));
            tagList.add(createTagMap("ReceiptCode", "9R", "Terminal generation unique transaction tracking number", false));
            tagList.add(createTagMap("OfflineAuthentication", "6a", "Offline chip payload cryptographic criteria metrics", false));
            tagList.add(createTagMap("TerminalModel", "6s", "Physical hardware device manufacture configuration name", false));
            tagList.add(createTagMap("TerminalSerialNumber", "6S", "Unique factory production serial reference stamp", false));
            tagList.add(createTagMap("TerminalPlatform", "6r", "Operating system execution runtime software index", false));
        }
        return tagList;
    }

    public List<Map<String, String>> getOutChannelFieldsByDialect(String dialect) {
        List<Map<String, String>> fields = new ArrayList<>();
        if (!"BIC_ISO".equalsIgnoreCase(dialect) && !"ALL".equalsIgnoreCase(dialect)) {
            return fields;
        }

        fields.add(createTagMap("f3_processingCode", "3", "ISO Field 3: Processing Code parameter constraint", true));
        fields.add(createTagMap("f4_amount", "4", "ISO Field 4: Direct clearing ledger raw valuation amount", true));
        fields.add(createTagMap("f7_dateTimeTransmission", "7", "ISO Field 7: Core network transmission timestamp", true));
        fields.add(createTagMap("f11_stan", "11", "ISO Field 11: Systems Trace Audit Number identifier index", true));
        fields.add(createTagMap("f12_timeCreate", "12", "ISO Field 12: Local core transaction creation time instance", true));
        fields.add(createTagMap("f13_dateCreate", "13", "ISO Field 13: Local core transaction creation logging date", true));
        fields.add(createTagMap("f14_cardExpire", "14", "ISO Field 14: Card expiration evaluation limits threshold", true));
        fields.add(createTagMap("f15_settlementDay", "15", "ISO Field 15: Target settlement day banking ledger coordinate", true));
        fields.add(createTagMap("f17_caputureDay", "17", "ISO Field 17: Merchant payload clearing capture date log", true));
        fields.add(createTagMap("f18_mcc", "18", "ISO Field 18: Merchant Category Code indexing classification", true));
        fields.add(createTagMap("f22_posEntryMode", "22", "ISO Field 22: Point of Service Data terminal entry code", true));
        fields.add(createTagMap("f25_posConditionCode", "25", "ISO Field 25: Point of Service condition status reason", true));
        fields.add(createTagMap("f27_authIdRespLen", "27", "ISO Field 27: Authorization identification response constraint", true));
        fields.add(createTagMap("f32_acquiringInstitutCode", "32", "ISO Field 32: Unique institutional identifier for Acquirer ID", true));
        fields.add(createTagMap("f33_forwardingInstitutIdCode", "33", "ISO Field 33: Outbound forwarding settlement node index", true));
        fields.add(createTagMap("f35_track2", "35", "ISO Field 35: High fidelity track 2 card storage data block", true));
        fields.add(createTagMap("f37_rrn", "37", "ISO Field 37: Retrieval Reference Number tracing key token", true));
        fields.add(createTagMap("f38_approvalCode", "38", "ISO Field 38: Authorization system reference approval token", true));
        fields.add(createTagMap("f39_responseCode", "39", "ISO Field 39: Core settlement node workflow response message", true));
        fields.add(createTagMap("f41_cardAcceptorTerminalId", "41", "ISO Field 41: Terminal unique operational device identifier", true));
        fields.add(createTagMap("f42_cardAcceptorIdCode", "42", "ISO Field 42: Merchant acceptor configuration identity code", true));
        fields.add(createTagMap("f43_cardAcceptorName", "43", "ISO Field 43: Merchant baseline localization city/region details", true));
        fields.add(createTagMap("f45_track1", "45", "ISO Field 45: Track 1 identity verification string block", true));
        fields.add(createTagMap("f48_retailerData", "48", "ISO Field 48: Retailer point of sale parameters dynamic payload", true));
        fields.add(createTagMap("f49_currency", "49", "ISO Field 49: Active clearing currency numeric ISO code token", true));
        fields.add(createTagMap("f52_pinBlock", "52", "ISO Field 52: Encrypted transaction personal PIN block container", true));
        fields.add(createTagMap("f54_additionalAmounts", "54", "ISO Field 54: Ledger cashback or tip balance calculations", true));
        fields.add(createTagMap("f56_bodo", "56", "ISO Field 56: Special network domain processing parameter data", true));
        fields.add(createTagMap("f57_tokens", "57", "ISO Field 57: Extended digital token mapping arrays (Long)", true));
        fields.add(createTagMap("f60_terminalData", "60", "ISO Field 60: Vendor point-of-sale operational architecture metrics", true));
        fields.add(createTagMap("f61_issuerCategRespData", "61", "ISO Field 61: Issuer categorical authorization workflow metadata", true));
        fields.add(createTagMap("f62_postalCode", "62", "ISO Field 62: Merchant validation geographical routing postal index", true));
        fields.add(createTagMap("f63_tokens", "63", "ISO Field 63: Digital credential verification tokens matrix", true));
        fields.add(createTagMap("f90_originalData", "90", "ISO Field 90: Original transaction logging parameters reference", true));
        fields.add(createTagMap("f95_replacementAmounts", "95", "ISO Field 95: Ledger replacement currency valuation offset adjustments", true));
        fields.add(createTagMap("f100_rcvInstitutIdCode", "100", "ISO Field 100: Receiver institution data routing index token", true));
        fields.add(createTagMap("f120_termAddresBranch", "120", "ISO Field 120: Physical hardware branch tracking terminal metric", true));
        fields.add(createTagMap("f121_authIndicator", "121", "ISO Field 121: Cryptographic authentication metric parameters", true));
        fields.add(createTagMap("f123_invoiceData", "123", "ISO Field 123: Linked commercial electronic invoice track log", true));
        fields.add(createTagMap("f124_batchAndShiftData", "124", "ISO Field 124: Financial batch auditing segment shift log data", true));
        fields.add(createTagMap("f125_settlementData", "125", "ISO Field 125: Clearing network target settlement parameter criteria", true));
        fields.add(createTagMap("f126_preauthtData", "126", "ISO Field 126: Open preauthorization hold argument definitions", true));
        fields.add(createTagMap("f127_userData", "127", "ISO Field 127: Custom payment domain dynamic string allocation placeholder", true));

        return fields;
    }

    private Map<String, String> createTagMap(String name, String tag, String baseDesc, boolean isOutbound) {
        Map<String, String> m = new HashMap<>();
        m.put("name", name);
        m.put("tag", tag);
        if (isOutbound) {
            m.put("description", outChannelInMemoryDescriptions.getOrDefault(name, baseDesc));
        } else {
            m.put("description", spdhInMemoryDescriptions.getOrDefault(name, baseDesc));
        }
        return m;
    }

    public void saveSpdhDescriptionMemory(String name, String desc) {
        spdhInMemoryDescriptions.put(name, desc);
    }

    public void saveOutChannelDescriptionMemory(String name, String desc) {
        outChannelInMemoryDescriptions.put(name, desc);
    }

    // Unused hardcoded lists preserved but made instance-bound just in case
    public List<Map<String, String>> getTransformerLineageMappings() { return new ArrayList<>(); }
    public List<Map<String, String>> getOutboundLineageMappings() { return new ArrayList<>(); }

    private static class DBeaverRow {
        String columnName;
        String dataType;
        String length;
        String jsonPathHint;
        String tableName;
    }
}