/*
 * Copyright © 2026 Peter Doornbosch
 *
 * This file is part of Flupke, a HTTP3 client Java library
 *
 * Flupke is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Flupke is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package tech.kwik.flupke.impl;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal implementation of RFC 9651 Structured Fields, limited to sf-string items and sf-list of sf-strings.
 */
public class StructuredFields {

    /**
     * Serializes a string as an sf-string (RFC 9651 Section 4.1.6).
     * Escapes {@code "} and {@code \} as required.
     */
    public static String serializeString(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Parses an sf-string item (RFC 9651 Section 4.2.5).
     *
     * @throws IllegalArgumentException if the input is not a valid sf-string
     */
    public static String parseString(String input) {
        String s = input.strip();
        if (s.isEmpty() || s.charAt(0) != '"') {
            throw new IllegalArgumentException("Not a valid sf-string: " + input);
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
                if (i >= s.length()) {
                    throw new IllegalArgumentException("Unterminated escape sequence in sf-string");
                }
                char escaped = s.charAt(i);
                if (escaped != '"' && escaped != '\\') {
                    throw new IllegalArgumentException("Invalid escape sequence in sf-string: \\" + escaped);
                }
                sb.append(escaped);
            }
            else if (c == '"') {
                return sb.toString();
            }
            else {
                sb.append(c);
            }
            i++;
        }
        throw new IllegalArgumentException("Unterminated sf-string");
    }

    /**
     * Serializes a list of strings as an sf-list of sf-strings (RFC 9651 Section 4.1.1).
     */
    public static String serializeStringList(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(serializeString(values.get(i)));
        }
        return sb.toString();
    }

    /**
     * Parses an sf-list of sf-strings from one or more header field values (RFC 9651 Section 4.2.1).
     * Multiple header field lines are treated as a comma-separated list per RFC 9110 Section 5.2.
     *
     * @throws IllegalArgumentException if the input cannot be parsed as an sf-list of sf-strings
     */
    public static List<String> parseStringList(List<String> headerValues) {
        String combined = String.join(", ", headerValues);
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < combined.length()) {
            // skip optional whitespace before item
            while (i < combined.length() && isOWS(combined.charAt(i))) {
                i++;
            }
            if (i >= combined.length()) {
                break;
            }
            if (combined.charAt(i) != '"') {
                throw new IllegalArgumentException("Expected sf-string at position " + i + " in: " + combined);
            }
            // parse sf-string
            StringBuilder sb = new StringBuilder();
            i++; // skip opening "
            while (i < combined.length()) {
                char c = combined.charAt(i);
                if (c == '\\') {
                    i++;
                    if (i >= combined.length()) {
                        throw new IllegalArgumentException("Unterminated escape sequence in sf-string");
                    }
                    char escaped = combined.charAt(i);
                    if (escaped != '"' && escaped != '\\') {
                        throw new IllegalArgumentException("Invalid escape sequence in sf-string: \\" + escaped);
                    }
                    sb.append(escaped);
                }
                else if (c == '"') {
                    break;
                }
                else {
                    sb.append(c);
                }
                i++;
            }
            if (i >= combined.length() || combined.charAt(i) != '"') {
                throw new IllegalArgumentException("Unterminated sf-string in: " + combined);
            }
            result.add(sb.toString());
            i++; // skip closing "
            // skip optional whitespace after item
            while (i < combined.length() && isOWS(combined.charAt(i))) {
                i++;
            }
            // expect comma separator or end
            if (i < combined.length()) {
                if (combined.charAt(i) != ',') {
                    throw new IllegalArgumentException("Expected ',' at position " + i + " in: " + combined);
                }
                i++; // skip comma
            }
        }
        return result;
    }

    private static boolean isOWS(char c) {
        return c == ' ' || c == '\t';
    }
}
