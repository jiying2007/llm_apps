package com.jingdu.txt.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RepairRulePackCodec {
    private static final String HEADER = "JINGDU-RULE-PACK\t1";
    private static final String FOOTER_PREFIX = "SHA256\t";
    private static final int MAXIMUM_PACK_BYTES = 1024 * 1024;
    private static final int MAXIMUM_RULES = 1000;

    public byte[] encode(List<RepairRule> inputRules) {
        validateRules(inputRules);
        List<RepairRule> rules = new ArrayList<RepairRule>(inputRules);
        Collections.sort(rules, Comparator.comparingInt(RepairRule::getOrder)
                .thenComparing(RepairRule::getId));
        StringBuilder body = new StringBuilder();
        body.append(HEADER).append('\n');
        for (RepairRule rule : rules) {
            body.append("RULE\t").append(field(rule.getId())).append('\t')
                    .append(field(rule.getMatchText())).append('\t')
                    .append(field(rule.getReplacement())).append('\t')
                    .append(rule.isEnabled() ? "1" : "0").append('\t')
                    .append(rule.getOrder()).append('\t')
                    .append(rule.getScope().name()).append('\t')
                    .append(field(rule.getNote())).append('\n');
        }
        byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
        String footer = FOOTER_PREFIX + sha256(bodyBytes) + "\n";
        byte[] footerBytes = footer.getBytes(StandardCharsets.UTF_8);
        if (bodyBytes.length + footerBytes.length > MAXIMUM_PACK_BYTES) {
            throw new IllegalArgumentException("repair rule pack exceeds 1 MiB");
        }
        byte[] result = new byte[bodyBytes.length + footerBytes.length];
        System.arraycopy(bodyBytes, 0, result, 0, bodyBytes.length);
        System.arraycopy(footerBytes, 0, result, bodyBytes.length, footerBytes.length);
        return result;
    }

    public List<RepairRule> decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAXIMUM_PACK_BYTES) {
            throw new IOException("repair rule pack size is invalid");
        }
        String text = decodeUtf8(bytes);
        int footerStart = text.lastIndexOf(FOOTER_PREFIX);
        if (footerStart <= 0 || text.charAt(footerStart - 1) != '\n') {
            throw new IOException("repair rule pack checksum is missing");
        }
        String footer = text.substring(footerStart);
        String[] footerParts = footer.split("\\n", -1);
        if (footerParts.length != 2 || !footerParts[1].isEmpty()
                || !footerParts[0].matches("SHA256\\t[0-9a-f]{64}")) {
            throw new IOException("repair rule pack footer is invalid");
        }
        String body = text.substring(0, footerStart);
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String expected = footerParts[0].substring(FOOTER_PREFIX.length());
        if (!constantTimeEquals(expected, sha256(bodyBytes))) {
            throw new IOException("repair rule pack checksum mismatch");
        }
        String[] lines = body.split("\\n", -1);
        if (lines.length < 2 || !HEADER.equals(lines[0])
                || !lines[lines.length - 1].isEmpty()) {
            throw new IOException("repair rule pack header is invalid");
        }
        if (lines.length - 2 > MAXIMUM_RULES) {
            throw new IOException("repair rule count exceeds " + MAXIMUM_RULES);
        }
        List<RepairRule> rules = new ArrayList<RepairRule>();
        Set<String> ids = new HashSet<String>();
        for (int index = 1; index < lines.length - 1; index++) {
            String[] fields = lines[index].split("\\t", -1);
            if (fields.length != 8 || !"RULE".equals(fields[0])) {
                throw new IOException("invalid repair rule line " + index);
            }
            try {
                String id = unfield(fields[1]);
                if (!ids.add(id)) {
                    throw new IOException("duplicate repair rule id: " + id);
                }
                String match = unfield(fields[2]);
                String replacement = unfield(fields[3]);
                boolean enabled;
                if ("1".equals(fields[4])) {
                    enabled = true;
                } else if ("0".equals(fields[4])) {
                    enabled = false;
                } else {
                    throw new IOException("invalid enabled value at line " + index);
                }
                int order = Integer.parseInt(fields[5]);
                RepairScope scope = RepairScope.valueOf(fields[6]);
                String note = unfield(fields[7]);
                RepairRule rule = new RepairRule(id, match, replacement, enabled, order,
                        scope, note);
                validateRule(rule);
                rules.add(rule);
            } catch (IllegalArgumentException invalid) {
                throw new IOException("invalid repair rule at line " + index, invalid);
            }
        }
        Collections.sort(rules, Comparator.comparingInt(RepairRule::getOrder)
                .thenComparing(RepairRule::getId));
        return rules;
    }

    private static void validateRules(List<RepairRule> rules) {
        if (rules == null || rules.size() > MAXIMUM_RULES) {
            throw new IllegalArgumentException("repair rule count is invalid");
        }
        Set<String> ids = new HashSet<String>();
        for (RepairRule rule : rules) {
            validateRule(rule);
            if (!ids.add(rule.getId())) {
                throw new IllegalArgumentException("duplicate repair rule id: " + rule.getId());
            }
        }
    }

    private static void validateRule(RepairRule rule) {
        if (rule == null || rule.getId().length() > RepairRule.MAXIMUM_FIELD_CHARACTERS
                || rule.getMatchText().length() > RepairRule.MAXIMUM_FIELD_CHARACTERS
                || rule.getReplacement().length() > RepairRule.MAXIMUM_FIELD_CHARACTERS
                || rule.getNote().length() > RepairRule.MAXIMUM_FIELD_CHARACTERS) {
            throw new IllegalArgumentException("repair rule field exceeds limit");
        }
    }

    private static String field(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unfield(String value) {
        byte[] decoded = value.isEmpty() ? new byte[0] : Base64.getUrlDecoder().decode(value);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(decoded)).toString();
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException("rule field is not UTF-8", invalid);
        }
    }

    private static String decodeUtf8(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalid) {
            throw new IOException("repair rule pack is not UTF-8", invalid);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xFF));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int difference = 0;
        for (int index = 0; index < left.length(); index++) {
            difference |= left.charAt(index) ^ right.charAt(index);
        }
        return difference == 0;
    }
}
