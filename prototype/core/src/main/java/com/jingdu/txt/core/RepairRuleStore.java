package com.jingdu.txt.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class RepairRuleStore {
    private static final int MAGIC = 0x4A445252;
    private static final int VERSION = 1;
    private static final int CATALOG_MAGIC = 0x4A445243;
    private static final int MAX_RULES = 1000;
    private static final int MAX_PROFILES = 10000;

    public void save(Path target, List<RepairRule> inputRules) throws IOException {
        if (inputRules.size() > MAX_RULES) {
            throw new IllegalArgumentException("rule count exceeds " + MAX_RULES);
        }
        List<RepairRule> rules = new ArrayList<RepairRule>(inputRules);
        Collections.sort(rules, Comparator.comparingInt(RepairRule::getOrder)
                .thenComparing(RepairRule::getId));
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("rule store needs a parent directory");
        }
        Files.createDirectories(parent);
        Path temporary = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.deleteIfExists(temporary);
        try {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temporary)))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(rules.size());
                for (RepairRule rule : rules) {
                    output.writeUTF(rule.getId());
                    output.writeUTF(rule.getMatchText());
                    output.writeUTF(rule.getReplacement());
                    output.writeBoolean(rule.isEnabled());
                    output.writeInt(rule.getOrder());
                    output.writeUTF(rule.getScope().name());
                    output.writeUTF(rule.getNote());
                }
            }
            atomicReplace(temporary, target);
        } catch (IOException error) {
            Files.deleteIfExists(temporary);
            throw error;
        }
    }

    public List<RepairRule> load(Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            return Collections.emptyList();
        }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(target)))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("unsupported repair rule store");
            }
            int count = input.readInt();
            if (count < 0 || count > MAX_RULES) {
                throw new IOException("invalid repair rule count: " + count);
            }
            List<RepairRule> rules = new ArrayList<RepairRule>(count);
            for (int index = 0; index < count; index++) {
                try {
                    rules.add(new RepairRule(input.readUTF(), input.readUTF(), input.readUTF(),
                            input.readBoolean(), input.readInt(),
                            RepairScope.valueOf(input.readUTF()), input.readUTF()));
                } catch (IllegalArgumentException invalid) {
                    throw new IOException("invalid repair rule at index " + index, invalid);
                }
            }
            if (input.read() != -1) {
                throw new IOException("trailing bytes in repair rule store");
            }
            return rules;
        }
    }

    public void saveCatalog(Path target, Map<String, List<RepairRule>> inputProfiles)
            throws IOException {
        if (inputProfiles.size() > MAX_PROFILES) {
            throw new IllegalArgumentException("profile count exceeds " + MAX_PROFILES);
        }
        for (Map.Entry<String, List<RepairRule>> entry : inputProfiles.entrySet()) {
            validateProfileKey(entry.getKey());
            if (entry.getValue().size() > MAX_RULES) {
                throw new IllegalArgumentException("rule count exceeds " + MAX_RULES
                        + " for profile " + entry.getKey());
            }
        }
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("rule catalog needs a parent directory");
        }
        Files.createDirectories(parent);
        Path temporary = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.deleteIfExists(temporary);
        try {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temporary)))) {
                output.writeInt(CATALOG_MAGIC);
                output.writeInt(VERSION);
                Map<String, List<RepairRule>> profiles = new TreeMap<String, List<RepairRule>>(
                        inputProfiles);
                output.writeInt(profiles.size());
                for (Map.Entry<String, List<RepairRule>> entry : profiles.entrySet()) {
                    output.writeUTF(entry.getKey());
                    List<RepairRule> rules = new ArrayList<RepairRule>(entry.getValue());
                    Collections.sort(rules, Comparator.comparingInt(RepairRule::getOrder)
                            .thenComparing(RepairRule::getId));
                    output.writeInt(rules.size());
                    for (RepairRule rule : rules) {
                        writeRule(output, rule);
                    }
                }
            }
            atomicReplace(temporary, target);
        } catch (IOException error) {
            Files.deleteIfExists(temporary);
            throw error;
        }
    }

    public Map<String, List<RepairRule>> loadCatalog(Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            return new LinkedHashMap<String, List<RepairRule>>();
        }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(target)))) {
            if (input.readInt() != CATALOG_MAGIC || input.readInt() != VERSION) {
                throw new IOException("unsupported repair rule catalog");
            }
            int profileCount = input.readInt();
            if (profileCount < 0 || profileCount > MAX_PROFILES) {
                throw new IOException("invalid repair profile count: " + profileCount);
            }
            Map<String, List<RepairRule>> profiles = new LinkedHashMap<String, List<RepairRule>>();
            for (int profileIndex = 0; profileIndex < profileCount; profileIndex++) {
                String key = input.readUTF();
                try {
                    validateProfileKey(key);
                } catch (IllegalArgumentException invalid) {
                    throw new IOException("invalid repair profile key", invalid);
                }
                int count = input.readInt();
                if (count < 0 || count > MAX_RULES) {
                    throw new IOException("invalid repair rule count: " + count);
                }
                if (profiles.containsKey(key)) {
                    throw new IOException("duplicate repair profile: " + key);
                }
                List<RepairRule> rules = new ArrayList<RepairRule>(count);
                for (int ruleIndex = 0; ruleIndex < count; ruleIndex++) {
                    rules.add(readRule(input, ruleIndex));
                }
                profiles.put(key, rules);
            }
            if (input.read() != -1) {
                throw new IOException("trailing bytes in repair rule catalog");
            }
            return profiles;
        }
    }

    private static void writeRule(DataOutputStream output, RepairRule rule) throws IOException {
        output.writeUTF(rule.getId());
        output.writeUTF(rule.getMatchText());
        output.writeUTF(rule.getReplacement());
        output.writeBoolean(rule.isEnabled());
        output.writeInt(rule.getOrder());
        output.writeUTF(rule.getScope().name());
        output.writeUTF(rule.getNote());
    }

    private static RepairRule readRule(DataInputStream input, int index) throws IOException {
        try {
            return new RepairRule(input.readUTF(), input.readUTF(), input.readUTF(),
                    input.readBoolean(), input.readInt(), RepairScope.valueOf(input.readUTF()),
                    input.readUTF());
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid repair rule at index " + index, invalid);
        }
    }

    private static void validateProfileKey(String key) {
        if (!("*".equals(key) || key.matches("[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("invalid repair profile key: " + key);
        }
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
