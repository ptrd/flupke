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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredFieldsTest {

    // region serializeString
    @Test
    void serializeStringProducesQuotedString() {
        assertThat(StructuredFields.serializeString("hello")).isEqualTo("\"hello\"");
    }

    @Test
    void serializeStringEscapesDoubleQuote() {
        assertThat(StructuredFields.serializeString("say \"hi\"")).isEqualTo("\"say \\\"hi\\\"\"");
    }

    @Test
    void serializeStringEscapesBackslash() {
        assertThat(StructuredFields.serializeString("a\\b")).isEqualTo("\"a\\\\b\"");
    }

    @Test
    void serializeEmptyStringProducesEmptyQuotedString() {
        assertThat(StructuredFields.serializeString("")).isEqualTo("\"\"");
    }
    // endregion

    // region parseString
    @Test
    void parseStringReturnsUnquotedValue() throws Exception {
        assertThat(StructuredFields.parseString("\"hello\"")).isEqualTo("hello");
    }

    @Test
    void parseStringHandlesEscapedDoubleQuote() throws Exception {
        assertThat(StructuredFields.parseString("\"say \\\"hi\\\"\"")).isEqualTo("say \"hi\"");
    }

    @Test
    void parseStringHandlesEscapedBackslash() throws Exception {
        assertThat(StructuredFields.parseString("\"a\\\\b\"")).isEqualTo("a\\b");
    }

    @Test
    void parseStringStripsLeadingAndTrailingWhitespace() throws Exception {
        assertThat(StructuredFields.parseString("  \"hello\"  ")).isEqualTo("hello");
    }

    @Test
    void parseEmptyQuotedStringReturnsEmptyString() throws Exception {
        assertThat(StructuredFields.parseString("\"\"")).isEqualTo("");
    }

    @Test
    void parseStringThrowsWhenNotStartingWithQuote() {
        assertThatThrownBy(() -> StructuredFields.parseString("hello"))
                .isInstanceOf(StructuredFieldsException.class);
    }

    @Test
    void parseStringThrowsWhenUnterminatedString() {
        assertThatThrownBy(() -> StructuredFields.parseString("\"hello"))
                .isInstanceOf(StructuredFieldsException.class);
    }

    @Test
    void parseStringThrowsOnInvalidEscapeSequence() {
        assertThatThrownBy(() -> StructuredFields.parseString("\"\\n\""))
                .isInstanceOf(StructuredFieldsException.class);
    }

    @Test
    void parseStringThrowsOnUnterminatedEscapeSequence() {
        assertThatThrownBy(() -> StructuredFields.parseString("\"\\"))
                .isInstanceOf(StructuredFieldsException.class);
    }
    // endregion

    // region serializeString / parseString roundtrip
    @Test
    void serializeAndParseRoundtrip() throws Exception {
        String original = "proto/1.0 with \"quotes\" and \\backslash\\";
        assertThat(StructuredFields.parseString(StructuredFields.serializeString(original))).isEqualTo(original);
    }
    // endregion

    // region serializeStringList
    @Test
    void serializeStringListProducesCommaSeparatedQuotedItems() {
        assertThat(StructuredFields.serializeStringList(List.of("proto-a", "proto-b")))
                .isEqualTo("\"proto-a\", \"proto-b\"");
    }

    @Test
    void serializeSingleItemListProducesSingleQuotedItem() {
        assertThat(StructuredFields.serializeStringList(List.of("only")))
                .isEqualTo("\"only\"");
    }

    @Test
    void serializeEmptyListProducesEmptyString() {
        assertThat(StructuredFields.serializeStringList(List.of())).isEqualTo("");
    }
    // endregion

    // region parseStringList
    @Test
    void parseStringListReturnsParsedItems() throws Exception {
        assertThat(StructuredFields.parseStringList(List.of("\"proto-a\", \"proto-b\"")))
                .containsExactly("proto-a", "proto-b");
    }

    @Test
    void parseStringListHandlesMultipleHeaderLines() throws Exception {
        assertThat(StructuredFields.parseStringList(List.of("\"proto-a\"", "\"proto-b\"")))
                .containsExactly("proto-a", "proto-b");
    }

    @Test
    void parseStringListHandlesWhitespaceAroundItems() throws Exception {
        assertThat(StructuredFields.parseStringList(List.of("  \"proto-a\"  ,  \"proto-b\"  ")))
                .containsExactly("proto-a", "proto-b");
    }

    @Test
    void parseStringListReturnsSingleItem() throws Exception {
        assertThat(StructuredFields.parseStringList(List.of("\"only\"")))
                .containsExactly("only");
    }

    @Test
    void parseEmptyStringListReturnsEmptyList() throws Exception {
        assertThat(StructuredFields.parseStringList(List.of())).isEmpty();
    }

    @Test
    void parseStringListThrowsWhenItemNotQuoted() {
        assertThatThrownBy(() -> StructuredFields.parseStringList(List.of("proto-a")))
                .isInstanceOf(StructuredFieldsException.class);
    }

    @Test
    void parseStringListThrowsWhenUnterminatedString() {
        assertThatThrownBy(() -> StructuredFields.parseStringList(List.of("\"proto-a")))
                .isInstanceOf(StructuredFieldsException.class);
    }
    // endregion

    // region serializeStringList / parseStringList roundtrip
    @Test
    void serializeAndParseListRoundtrip() throws Exception {
        List<String> original = List.of("proto-a", "proto/b", "proto with \"quotes\"");
        assertThat(StructuredFields.parseStringList(List.of(StructuredFields.serializeStringList(original))))
                .isEqualTo(original);
    }
    // endregion
}
