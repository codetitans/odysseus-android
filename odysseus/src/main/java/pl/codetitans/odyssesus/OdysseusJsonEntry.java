package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;

/**
 * Implemented by entries that know how to serialize themselves into a single JSON object.
 */
interface OdysseusJsonEntry {
    void writeJson(@NonNull StringBuilder sb);

    /**
     * Gets a JSON representation of this object.
     */
    @NonNull
    default String toJsonString() {
        final StringBuilder sb = new StringBuilder();
        writeJson(sb);
        return sb.toString();
    }
}
