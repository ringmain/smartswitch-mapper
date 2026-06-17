package com.paymentswitch.mapper;

import com.paymentswitch.mapper.dto.MappingDtos.TransformerMapping;
import com.paymentswitch.mapper.dto.MappingDtos.OutboundMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MappingController {

    private final MappingService mappingService;
    private final AutomatedLineageService automatedLineageService;

    @Autowired
    public MappingController(MappingService mappingService, AutomatedLineageService automatedLineageService) {
        this.mappingService = mappingService;
        this.automatedLineageService = automatedLineageService;
    }

    @GetMapping("/transformer-mappings")
    public List<TransformerMapping> getTransformerMappings(@RequestParam(defaultValue = "GENERIC") String dialect) {
        List<TransformerMapping> manualMappings = mappingService.getTransformerMappings().stream()
                .filter(m -> dialect.equalsIgnoreCase(m.channelDialect()))
                .toList();

        List<TransformerMapping> allMappings = new ArrayList<>(manualMappings);

        if ("AUTO_SPDH_GENERIC".equalsIgnoreCase(dialect)) {
            allMappings.addAll(automatedLineageService.extractInboundMappings());
        }
        return allMappings;
    }

    @PostMapping("/transformer-mappings")
    public TransformerMapping saveTransformerMapping(@RequestBody TransformerMapping newMapping) {
        return mappingService.saveTransformerMapping(newMapping);
    }

    @PostMapping("/transformer-mappings/delete")
    public void deleteTransformerMapping(@RequestBody TransformerMapping targetMapping) {
        mappingService.deleteTransformerMapping(targetMapping);
    }

    @PostMapping("/transformer-mappings/delete-batch")
    public void deleteTransformerMappingsBatch(@RequestBody List<TransformerMapping> targetMappings) {
        mappingService.deleteTransformerMappingsBatch(targetMappings);
    }

    @GetMapping("/outbound-mappings")
    public List<OutboundMapping> getOutboundMappings(@RequestParam(defaultValue = "GENERIC") String dialect) {
        List<OutboundMapping> manualMappings = mappingService.getOutboundMappings().stream()
                .filter(m -> dialect.equalsIgnoreCase(m.channelDialect()))
                .toList();

        List<OutboundMapping> allMappings = new ArrayList<>(manualMappings);

        if ("AUTO_BIC_ISO_GENERIC".equalsIgnoreCase(dialect)) {
            allMappings.addAll(automatedLineageService.extractOutboundMappings());
        }
        return allMappings;
    }

    @PostMapping("/outbound-mappings")
    public OutboundMapping saveOutboundMapping(@RequestBody OutboundMapping newMapping) {
        return mappingService.saveOutboundMapping(newMapping);
    }

    @PostMapping("/outbound-mappings/delete")
    public void deleteOutboundMapping(@RequestBody OutboundMapping targetMapping) {
        mappingService.deleteOutboundMapping(targetMapping);
    }

    @PostMapping("/outbound-mappings/delete-batch")
    public void deleteOutboundMappingsBatch(@RequestBody List<OutboundMapping> targetMappings) {
        mappingService.deleteOutboundMappingsBatch(targetMappings);
    }
}