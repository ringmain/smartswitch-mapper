package com.paymentswitch.mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MapperController {

    private final JsonModelParser schemaService;
    private final SpdhParserService spdhParserService;

    @Autowired
    public MapperController(JsonModelParser schemaService, SpdhParserService spdhParserService) {
        this.schemaService = schemaService;
        this.spdhParserService = spdhParserService;
    }

    @GetMapping("/schema")
    public Map<String, Object> getSchema() {
        try {
            return schemaService.loadCombinedSchemaAndMetadata();
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("error", "Parsing error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/metadata")
    public Map<String, String> updateMetadata(@RequestBody Map<String, Object> payload) {
        try {
            String path = (String) payload.get("path");
            Map<String, String> params = (Map<String, String>) payload.get("parameters");
            schemaService.saveAttributeMetadata(path, params);
            return Map.of("status", "success");
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("status", "error");
        }
    }

    // STRICT ARRAY SPLITTING FIX
    @GetMapping("/spdh-tags")
    public List<Map<String, String>> getSpdhTags(@RequestParam(defaultValue = "BaseSpdhTag") String dialects) {
        List<String> dialectList = Arrays.stream(dialects.split(","))
                .map(String::trim)
                .toList();
        return spdhParserService.parseDialects(dialectList);
    }

    @PostMapping("/spdh-description")
    public Map<String, String> updateSpdhDescription(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String description = payload.get("description");
        schemaService.saveSpdhDescriptionMemory(name, description);
        return Map.of("status", "success");
    }

    @GetMapping("/outbound-fields")
    public List<Map<String, String>> getOutboundFields(@RequestParam(defaultValue = "NONE") String dialect) {
        return schemaService.getOutChannelFieldsByDialect(dialect);
    }

    @PostMapping("/outbound-description")
    public Map<String, String> updateOutboundDescription(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String description = payload.get("description");
        schemaService.saveOutChannelDescriptionMemory(name, description);
        return Map.of("status", "success");
    }
}