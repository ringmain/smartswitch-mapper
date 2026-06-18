package com.paymentswitch.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentswitch.mapper.dto.MappingDtos.TransformerMapping;
import com.paymentswitch.mapper.dto.MappingDtos.OutboundMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class MappingService {

    private static final Logger log = LoggerFactory.getLogger(MappingService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final File transformerFile;
    private final File outboundFile;

    public MappingService(
            @Value("${mapper.file.transformer:transformer-mappings.json}") String transformerFilePath,
            @Value("${mapper.file.outbound:outbound-mappings.json}") String outboundFilePath) {
        this.transformerFile = new File(transformerFilePath);
        this.outboundFile = new File(outboundFilePath);
    }

    // Added synchronized to prevent file corruption from concurrent requests
    private synchronized <T> List<T> loadMappingsFromFile(File file, TypeReference<List<T>> typeReference) {
        if (!file.exists()) return new ArrayList<>();
        try {
            return objectMapper.readValue(file, typeReference);
        } catch (IOException e) {
            log.error("Failed to read mapping configuration from file: {}", file.getName(), e);
            return new ArrayList<>();
        }
    }

    private synchronized <T> void saveMappingsToFile(File file, List<T> mappings) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, mappings);
        } catch (IOException e) {
            log.error("Failed to persist updated mapping config to file: {}", file.getName(), e);
        }
    }

    // --- Transformer (Inbound) Logic ---
    public List<TransformerMapping> getTransformerMappings() {
        return loadMappingsFromFile(transformerFile, new TypeReference<>() {});
    }

    public TransformerMapping saveTransformerMapping(TransformerMapping newMapping) {
        List<TransformerMapping> currentMappings = getTransformerMappings();
        boolean exists = currentMappings.stream().anyMatch(m ->
                m.ctrxPath().equalsIgnoreCase(newMapping.ctrxPath()) &&
                        m.fieldName().equalsIgnoreCase(newMapping.fieldName()) &&
                        m.channelDialect().equalsIgnoreCase(newMapping.channelDialect()) &&
                        (m.type() != null ? m.type() : "REQUEST").equalsIgnoreCase(newMapping.type() != null ? newMapping.type() : "REQUEST")
        );

        if (!exists) {
            currentMappings.add(newMapping);
            saveMappingsToFile(transformerFile, currentMappings);
        }
        return newMapping;
    }

    public void deleteTransformerMapping(TransformerMapping targetMapping) {
        List<TransformerMapping> currentMappings = getTransformerMappings();
        currentMappings.removeIf(m ->
                m.ctrxPath().equalsIgnoreCase(targetMapping.ctrxPath()) &&
                        m.fieldName().equalsIgnoreCase(targetMapping.fieldName()) &&
                        m.channelDialect().equalsIgnoreCase(targetMapping.channelDialect()) &&
                        (m.type() != null ? m.type() : "REQUEST").equalsIgnoreCase(targetMapping.type() != null ? targetMapping.type() : "REQUEST")
        );
        saveMappingsToFile(transformerFile, currentMappings);
    }

    // NEW: Batch Deletion
    public void deleteTransformerMappingsBatch(List<TransformerMapping> targets) {
        if (targets == null || targets.isEmpty()) return;
        List<TransformerMapping> currentMappings = getTransformerMappings();
        boolean removedAny = currentMappings.removeIf(m ->
                targets.stream().anyMatch(t ->
                        m.ctrxPath().equalsIgnoreCase(t.ctrxPath()) &&
                                m.fieldName().equalsIgnoreCase(t.fieldName()) &&
                                m.channelDialect().equalsIgnoreCase(t.channelDialect()) &&
                                (m.type() != null ? m.type() : "REQUEST").equalsIgnoreCase(t.type() != null ? t.type() : "REQUEST")
                )
        );
        if (removedAny) saveMappingsToFile(transformerFile, currentMappings);
    }

    // --- Outbound Logic ---
    public List<OutboundMapping> getOutboundMappings() {
        return loadMappingsFromFile(outboundFile, new TypeReference<>() {});
    }

    public OutboundMapping saveOutboundMapping(OutboundMapping newMapping) {
        List<OutboundMapping> currentMappings = getOutboundMappings();
        boolean exists = currentMappings.stream().anyMatch(m ->
                m.ctrxPath().equalsIgnoreCase(newMapping.ctrxPath()) &&
                        m.fieldName().equalsIgnoreCase(newMapping.fieldName()) &&
                        m.channelDialect().equalsIgnoreCase(newMapping.channelDialect()) &&
                        (m.type() != null ? m.type() : "REQUEST").equalsIgnoreCase(newMapping.type() != null ? newMapping.type() : "REQUEST")
        );

        if (!exists) {
            currentMappings.add(newMapping);
            saveMappingsToFile(outboundFile, currentMappings);
        }
        return newMapping;
    }

    public void deleteOutboundMapping(OutboundMapping targetMapping) {
        List<OutboundMapping> currentMappings = getOutboundMappings();
        currentMappings.removeIf(m ->
                m.ctrxPath().equalsIgnoreCase(targetMapping.ctrxPath()) &&
                        m.fieldName().equalsIgnoreCase(targetMapping.fieldName()) &&
                        m.channelDialect().equalsIgnoreCase(targetMapping.channelDialect()) &&
                        (m.type() != null ? m.type() : "REQUEST").equalsIgnoreCase(targetMapping.type() != null ? targetMapping.type() : "REQUEST")
        );
        saveMappingsToFile(outboundFile, currentMappings);
    }

    // NEW: Batch Deletion
    public void deleteOutboundMappingsBatch(List<OutboundMapping> targets) {
        if (targets == null || targets.isEmpty()) return;
        List<OutboundMapping> currentMappings = getOutboundMappings();
        boolean removedAny = currentMappings.removeIf(m ->
                targets.stream().anyMatch(t ->
                        m.ctrxPath().equalsIgnoreCase(t.ctrxPath()) &&
                                m.fieldName().equalsIgnoreCase(t.fieldName()) &&
                                m.channelDialect().equalsIgnoreCase(t.channelDialect()) &&
                                (m.type() != null ? m.type() : "REQUEST").equalsIgnoreCase(t.type() != null ? t.type() : "REQUEST")
                )
        );
        if (removedAny) saveMappingsToFile(outboundFile, currentMappings);
    }
}