package com.osfans.trime.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;



/**
 * Rime Configuration Access Class.
 *
 * This class provides methods to read and write configuration values
 * from Rime configuration files via JNI, simulating the original Kotlin structure.
 */
public final class RimeConfig implements AutoCloseable {

    private final long peer;

    // --- Functional Interface for getConfigList ---

    /**
     * Functional interface to define the action for retrieving an item from the RimeConfig list.
     * Replaces the Kotlin extension function lambda (RimeConfig.(String) -> E?).
     *
     * @param <E> The expected type of the configuration item.
     */
    @FunctionalInterface
    public interface RimeConfigAction<E> {
        /**
         * Action to be performed on a RimeConfig instance to get a value.
         *
         * @param config The RimeConfig instance.
         * @param path The path/key to the configuration item.
         * @return The retrieved value, or null if retrieval failed.
         */
        E get(RimeConfig config, String path);
    }

    // --- Constructor ---

    private RimeConfig(long peer) {
        if (peer == 0) {
            throw new IllegalArgumentException("RimeConfig peer must not be 0.");
        }
        this.peer = peer;
    }

    // --- Public Getters ---

    /**
     * Gets an integer value from the configuration.
     * @param key The configuration key.
     * @return The integer value, or null if the key is not found or not an integer.
     */
    public Integer getInt(String key) {
        // JNI function returns Integer (which is nullable), matching Kotlin's return type.
        return getRimeConfigInt(peer, key);
    }

    /**
     * Gets a string value from the configuration.
     * @param key The configuration key.
     * @return The string value, or null if the key is not found or not a string.
     */
    public String getString(String key) {
        // JNI function returns String (which is nullable), matching Kotlin's return type.
        return getRimeConfigString(peer, key);
    }

    /**
     * Gets a list of configuration items by iterating over the list item paths.
     *
     * @param key The configuration key pointing to a list structure.
     * @param getAction The action to retrieve the specific type {@code E} from the list path.
     * @param <E> The expected type of the list element.
     * @return A list of retrieved values of type {@code E}.
     */
    public <E> List<E> getList(String key, RimeConfigAction<E> getAction) {
        // JNI returns Array<String>
        String[] paths = getRimeConfigListItemPath(peer, key);

        // Pre-allocate list size
        List<E> values = new ArrayList<>(paths.length);

        for (String path : paths) {
            // Replaces the Kotlin extension call: val value = getAction(this, path)
            E value = getAction.get(this, path);

            if (value == null) {
                // Log the failure to retrieve the expected item
                String stringValue = getString(path);
                //Timber.w("Failed to get value '%s' as expected on path '%s'", stringValue, path);
                continue;
            }
            values.add(value);
        }
        return values;
    }

    // --- Public Setter ---

    /**
     * Sets a boolean value in the configuration.
     * @param key The configuration key.
     * @param value The boolean value to set.
     */
    public void setBool(String key, boolean value) {
        setRimeConfigBool(peer, key, value);
    }

    // --- AutoCloseable Implementation ---

    /**
     * Closes the underlying Rime configuration handle.
     */
    @Override
    public void close() {
        closeRimeConfig(peer);
    }

    // --- Static Factory Methods ---

    /**
     * Opens a Rime configuration file for reading.
     * @param configId The ID of the config file (e.g., "default").
     * @return A new RimeConfig instance.
     * @throws IllegalArgumentException if the config could not be opened.
     */
    public static RimeConfig openConfig(String configId) {
        long peer = openRimeConfig(configId);
        if (peer == 0) {
            throw new IllegalArgumentException("Failed to open Rime config: " + configId);
        }
        return new RimeConfig(peer);
    }

    /**
     * Opens a Rime user configuration file.
     * @param configId The ID of the user config file.
     * @return A new RimeConfig instance.
     * @throws IllegalArgumentException if the user config could not be opened.
     */
    public static RimeConfig openUserConfig(String configId) {
        long peer = openRimeUserConfig(configId);
        if (peer == 0) {
            throw new IllegalArgumentException("Failed to open Rime user config: " + configId);
        }
        return new RimeConfig(peer);
    }

    /**
     * Opens a Rime schema configuration file.
     * @param schemaId The ID of the schema.
     * @return A new RimeConfig instance.
     * @throws IllegalArgumentException if the schema could not be opened.
     */
    public static RimeConfig openSchema(String schemaId) {
        long peer = openRimeSchema(schemaId);
        if (peer == 0) {
            throw new IllegalArgumentException("Failed to open Rime schema: " + schemaId);
        }
        return new RimeConfig(peer);
    }

    // --- JNI Declarations (Companion Object) ---

    // Note: These methods are static and private, mirroring the Kotlin companion object structure.

    private static native long openRimeConfig(String configId);

    private static native long openRimeUserConfig(String configId);

    private static native long openRimeSchema(String schemaId);

    private static native Integer getRimeConfigInt(long peer, String key);

    private static native String getRimeConfigString(long peer, String key);

    private static native String[] getRimeConfigListItemPath(long peer, String key);

    private static native void setRimeConfigBool(long peer, String key, boolean value);

    private static native void closeRimeConfig(long peer);
}
