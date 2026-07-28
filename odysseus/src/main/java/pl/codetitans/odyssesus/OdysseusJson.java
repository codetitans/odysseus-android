package pl.codetitans.odyssesus;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Minimal hand-rolled JSON writer used to serialize log/event entries without pulling in an external JSON library.
 */
final class OdysseusJson {

    private OdysseusJson() {
    }

    static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString(sb, (String) value);
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Date) {
            writeDate(sb, (Date) value);
        } else if (value instanceof Dictionary) {
            writeDictionary(sb, (Dictionary<?, ?>) value);
        } else if (value instanceof Map) {
            writeMap(sb, (Map<?, ?>) value);
        } else if (value instanceof List) {
            writeList(sb, (List<?>) value);
        } else if (value instanceof Object[]) {
            writeArray(sb, (Object[]) value);
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void writeDate(StringBuilder sb, Date value) {
        // A new instance per call, since SimpleDateFormat is not thread-safe.
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        writeString(sb, format.format(value));
    }

    private static void writeDictionary(StringBuilder sb, Dictionary<?, ?> dict) {
        sb.append('{');
        final Enumeration<?> keys = dict.keys();
        boolean first = true;
        while (keys.hasMoreElements()) {
            final Object key = keys.nextElement();
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(sb, String.valueOf(key));
            sb.append(':');
            writeValue(sb, dict.get(key));
        }
        sb.append('}');
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(sb, String.valueOf(entry.getKey()));
            sb.append(':');
            writeValue(sb, entry.getValue());
        }
        sb.append('}');
    }

    private static void writeList(StringBuilder sb, List<?> list) {
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            writeValue(sb, list.get(i));
        }
        sb.append(']');
    }

    private static void writeArray(StringBuilder sb, Object[] array) {
        sb.append('[');
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            writeValue(sb, array[i]);
        }
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
