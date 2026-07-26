package pl.codetitans.odyssesus;

import androidx.annotation.NonNull;

/**
 * Implemented by entries that know how to serialize themselves into a single JSON object.
 */
interface OdysseusJsonEntry {
    void writeJson(@NonNull StringBuilder sb);
}
