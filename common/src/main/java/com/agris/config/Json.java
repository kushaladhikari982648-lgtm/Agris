package com.agris.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dependency-free JSON parser and deterministic pretty-printer supporting RFC 8259
 * plus {@code //} and {@code /* *&#47;} comments and trailing commas.
 */
public final class Json {
    private Json() {
    }

    public static Object parse(final String source) {
        if (source == null) {
            throw new IllegalArgumentException("JSON source must not be null");
        }
        final Parser parser = new Parser(source);
        final Object value = parser.readValue();
        parser.skipWhitespaceAndComments();
        if (!parser.eof()) {
            throw parser.fail("Unexpected trailing content");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(final String source) {
        final Object parsed = parse(source);
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Expected root JSON object, found "
                    + (parsed == null ? "null" : parsed.getClass().getSimpleName()));
        }
        return (Map<String, Object>) parsed;
    }

    public static String stringify(final Object value) {
        final StringBuilder out = new StringBuilder(256);
        writeValue(out, value, 0);
        out.append('\n');
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(final StringBuilder out, final Object value, final int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            out.append(value);
        } else if (value instanceof Number num) {
            final double d = num.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                out.append("0");
            } else if (d == Math.rint(d) && Math.abs(d) < 1.0e15) {
                out.append((long) d);
            } else {
                out.append(d);
            }
        } else if (value instanceof String str) {
            writeString(out, str);
        } else if (value instanceof Map<?, ?> rawMap) {
            final Map<String, Object> map = (Map<String, Object>) rawMap;
            if (map.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            int index = 0;
            final int size = map.size();
            for (final Map.Entry<String, Object> entry : map.entrySet()) {
                indent(out, depth + 1);
                writeString(out, String.valueOf(entry.getKey()));
                out.append(": ");
                writeValue(out, entry.getValue(), depth + 1);
                if (++index < size) {
                    out.append(',');
                }
                out.append('\n');
            }
            indent(out, depth);
            out.append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                out.append("[]");
                return;
            }
            out.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                indent(out, depth + 1);
                writeValue(out, list.get(i), depth + 1);
                if (i + 1 < list.size()) {
                    out.append(',');
                }
                out.append('\n');
            }
            indent(out, depth);
            out.append(']');
        } else {
            writeString(out, value.toString());
        }
    }

    private static void indent(final StringBuilder out, final int depth) {
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
    }

    private static void writeString(final StringBuilder out, final String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String src;
        private int pos;

        Parser(final String src) {
            this.src = src;
        }

        boolean eof() {
            return this.pos >= this.src.length();
        }

        void skipWhitespaceAndComments() {
            while (this.pos < this.src.length()) {
                final char c = this.src.charAt(this.pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    this.pos++;
                } else if (c == '/' && this.pos + 1 < this.src.length()) {
                    final char next = this.src.charAt(this.pos + 1);
                    if (next == '/') {
                        this.pos += 2;
                        while (this.pos < this.src.length() && this.src.charAt(this.pos) != '\n') {
                            this.pos++;
                        }
                    } else if (next == '*') {
                        this.pos += 2;
                        final int end = this.src.indexOf("*/", this.pos);
                        if (end < 0) {
                            throw fail("Unterminated block comment");
                        }
                        this.pos = end + 2;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
        }

        Object readValue() {
            skipWhitespaceAndComments();
            if (eof()) {
                throw fail("Unexpected end of input");
            }
            final char c = this.src.charAt(this.pos);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield readNumber();
                    }
                    throw fail("Unexpected character '" + c + "'");
                }
            };
        }

        Map<String, Object> readObject() {
            this.pos++; // '{'
            final Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespaceAndComments();
            if (!eof() && this.src.charAt(this.pos) == '}') {
                this.pos++;
                return map;
            }
            while (true) {
                skipWhitespaceAndComments();
                if (!eof() && this.src.charAt(this.pos) == '}') {
                    this.pos++;
                    return map;
                }
                if (eof() || this.src.charAt(this.pos) != '"') {
                    throw fail("Expected quoted object key");
                }
                final String key = readString();
                skipWhitespaceAndComments();
                if (eof() || this.src.charAt(this.pos) != ':') {
                    throw fail("Expected ':' after key '" + key + "'");
                }
                this.pos++;
                final Object val = readValue();
                map.put(key, val);
                skipWhitespaceAndComments();
                if (eof()) {
                    throw fail("Unterminated object");
                }
                final char sep = this.src.charAt(this.pos);
                if (sep == ',') {
                    this.pos++;
                } else if (sep == '}') {
                    this.pos++;
                    return map;
                } else {
                    throw fail("Expected ',' or '}' in object");
                }
            }
        }

        List<Object> readArray() {
            this.pos++; // '['
            final List<Object> list = new ArrayList<>();
            skipWhitespaceAndComments();
            if (!eof() && this.src.charAt(this.pos) == ']') {
                this.pos++;
                return list;
            }
            while (true) {
                skipWhitespaceAndComments();
                if (!eof() && this.src.charAt(this.pos) == ']') {
                    this.pos++;
                    return list;
                }
                list.add(readValue());
                skipWhitespaceAndComments();
                if (eof()) {
                    throw fail("Unterminated array");
                }
                final char sep = this.src.charAt(this.pos);
                if (sep == ',') {
                    this.pos++;
                } else if (sep == ']') {
                    this.pos++;
                    return list;
                } else {
                    throw fail("Expected ',' or ']' in array");
                }
            }
        }

        String readString() {
            this.pos++; // '"'
            final StringBuilder out = new StringBuilder();
            while (this.pos < this.src.length()) {
                final char c = this.src.charAt(this.pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (eof()) {
                        throw fail("Unterminated escape sequence");
                    }
                    final char esc = this.src.charAt(this.pos++);
                    switch (esc) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (this.pos + 4 > this.src.length()) {
                                throw fail("Incomplete unicode escape");
                            }
                            final String hex = this.src.substring(this.pos, this.pos + 4);
                            this.pos += 4;
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                            } catch (final NumberFormatException ex) {
                                throw fail("Invalid unicode escape \\u" + hex);
                            }
                        }
                        default -> throw fail("Invalid escape '\\" + esc + "'");
                    }
                } else {
                    out.append(c);
                }
            }
            throw fail("Unterminated string literal");
        }

        Object readLiteral(final String literal, final Object result) {
            if (!this.src.startsWith(literal, this.pos)) {
                throw fail("Expected '" + literal + "'");
            }
            this.pos += literal.length();
            return result;
        }

        Number readNumber() {
            final int start = this.pos;
            if (this.src.charAt(this.pos) == '-') {
                this.pos++;
            }
            boolean isFloat = false;
            while (this.pos < this.src.length()) {
                final char c = this.src.charAt(this.pos);
                if (c >= '0' && c <= '9') {
                    this.pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    isFloat = true;
                    this.pos++;
                } else {
                    break;
                }
            }
            final String token = this.src.substring(start, this.pos);
            try {
                if (isFloat) {
                    return Double.valueOf(token);
                }
                final long l = Long.parseLong(token);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    return (int) l;
                }
                return l;
            } catch (final NumberFormatException ex) {
                throw fail("Malformed number '" + token + "'");
            }
        }

        IllegalArgumentException fail(final String message) {
            return new IllegalArgumentException(message + " at offset " + this.pos);
        }
    }
}
