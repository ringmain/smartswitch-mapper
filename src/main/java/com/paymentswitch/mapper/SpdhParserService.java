package com.paymentswitch.mapper;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SpdhParserService {

    public List<Map<String, String>> parseDialects(List<String> requestedDialects) {
        List<Map<String, String>> results = new ArrayList<>();
        if (requestedDialects == null || requestedDialects.isEmpty() || requestedDialects.contains("NONE")) {
            System.out.println("[SPDH PARSER] No dialects requested by frontend. Returning empty.");
            return results;
        }

        System.out.println("\n==================================================");
        System.out.println("[SPDH PARSER] Frontend Requested Dialects: " + requestedDialects);

        try {
            // 1. Try to find the folder using multiple strategy paths (UPDATED PATH)
            File folder = new File("src/main/resources/automated-dialects/spdh_tags");

            if (!folder.exists()) {
                folder = new File(System.getProperty("user.dir"), "src/main/resources/automated-dialects/spdh_tags");
            }
            if (!folder.exists()) {
                folder = Paths.get("").toAbsolutePath().resolve("src/main/resources/automated-dialects/spdh_tags").toFile();
            }

            System.out.println("[SPDH PARSER] Target Directory: " + folder.getAbsolutePath());

            if (folder.exists() && folder.isDirectory()) {
                File[] files = folder.listFiles((dir, name) -> name.endsWith(".java"));

                if (files != null && files.length > 0) {
                    System.out.println("[SPDH PARSER] Found " + files.length + " java files in the directory.");

                    for (File file : files) {
                        String dialectName = file.getName().replace(".java", "");

                        if (requestedDialects.contains(dialectName)) {
                            System.out.println("[SPDH PARSER] --> Parsing file: " + file.getName());
                            List<Map<String, String>> parsedTags = parseFile(file, dialectName);
                            System.out.println("[SPDH PARSER] --> Extracted " + parsedTags.size() + " tags from " + dialectName);
                            results.addAll(parsedTags);
                        }
                    }
                } else {
                    System.out.println("[SPDH PARSER] WARNING: Directory exists but contains no .java files!");
                }
            } else {
                System.out.println("[SPDH PARSER] ERROR: The spdh_tags directory DOES NOT EXIST at this path!");
            }
            System.out.println("==================================================\n");

        } catch (Exception e) {
            System.out.println("[SPDH PARSER] SEVERE ERROR:");
            e.printStackTrace();
        }
        return results;
    }

    private List<Map<String, String>> parseFile(File file, String dialectName) throws Exception {
        List<Map<String, String>> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder javadoc = new StringBuilder();
            boolean inJavadoc = false;

            // Highly forgiving regex to handle spaces, tabs, and weird formatting
            Pattern enumPattern = Pattern.compile("^\\s*([A-Za-z0-9_]+)\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.startsWith("/**")) {
                    inJavadoc = true;
                    javadoc = new StringBuilder();
                } else if (trimmed.startsWith("*/")) {
                    inJavadoc = false;
                } else if (inJavadoc) {
                    String clean = trimmed.replaceFirst("^\\*\\s?", "").trim();
                    if (!clean.isEmpty()) {
                        javadoc.append(clean).append(" ");
                    }
                } else {
                    Matcher m = enumPattern.matcher(line);
                    if (m.find()) {
                        Map<String, String> map = new HashMap<>();
                        map.put("name", m.group(1));
                        map.put("tag", m.group(2));
                        map.put("description", javadoc.toString().trim());
                        map.put("dialect", dialectName);
                        list.add(map);

                        javadoc = new StringBuilder(); // Reset documentation block
                    }
                }
            }
        }
        return list;
    }
}