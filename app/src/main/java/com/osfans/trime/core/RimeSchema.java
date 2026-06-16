package com.osfans.trime.core;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Represents the configuration and structure of a Rime input schema.
 * This class loads its properties from Rime configuration files.
 */
public final class RimeSchema {

    private final String schemaId;
    private  List<Switch> switches;
    private  String alphabet;

    // --- Switch Data Class ---

    public static final class Switch {
        private final String name;
        private final List<String> options;
        private int reset;
        private final List<String> states;

        // Default constructor matching Kotlin's default arguments
        public Switch() {
            this("", Collections.emptyList(), 0, Collections.emptyList());
        }

        public Switch(String name, List<String> options, int reset, List<String> states) {
            this.name = name;
            this.options = (options != null) ? options : Collections.emptyList();
            this.reset = reset;
            this.states = (states != null) ? states : Collections.emptyList();
        }

        public String getName() {
            return name;
        }

        public List<String> getOptions() {
            return options;
        }

        public int getReset() {
            return reset;
        }

        public List<String> getStates() {
            return states;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Switch aSwitch = (Switch) o;
            return reset == aSwitch.reset &&
                    Objects.equals(name, aSwitch.name) &&
                    Objects.equals(options, aSwitch.options) &&
                    Objects.equals(states, aSwitch.states);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, options, reset, states);
        }

        @Override
        public String toString() {
            return "Switch(name='" + name + "', options=" + options + ", reset=" + reset + ", states=" + states + ")";
        }

        public String getState() {
            if (!options.isEmpty()) {
                return getStates().get(reset);
            } else {
                return getStates().get(Rime.getRimeOption(getName()) ? 1 : 0);
            }
        }
        public String getOption() {
            if (!options.isEmpty()) {
                return options.get(reset);
            }
            return null;
        }
        public String getUnState() {
            if (!options.isEmpty()) {
                return getStates().get((reset + 1) % options.size());
            } else {
                return getStates().get(Rime.getRimeOption(getName()) ? 0 : 1);
            }
        }

        public void toggleOption() {
            if (!options.isEmpty()) {
                Rime.setRimeOption(options.get(reset), false);
                reset = (reset + 1) % options.size();
                Rime.setRimeOption(options.get(reset), true);
            } else {
                Log.w("TAG", "toggleOption:1 "+reset );
                reset = 1 - reset;
                Log.w("TAG", "toggleOption:2 "+reset );
                Rime.setRimeOption(getName(), reset == 1);
            }
        }
    }

    // --- Constructor (Replaces Kotlin's Primary Constructor and init block) ---

    public RimeSchema(String schemaId) {
        this.schemaId = schemaId;
        RimeConfig schemaConfig;

        // Equivalent of Kotlin's 'when' expression for opening the config
        if (schemaId == null || schemaId.isEmpty()) {
            schemaConfig = RimeConfig.openConfig("default");
        } else if (schemaId.startsWith(".")) {
            schemaConfig = RimeConfig.openSchema(schemaId.substring(1));
        } else {
            schemaConfig = RimeConfig.openSchema(schemaId);
        }

        // Equivalent of Kotlin's 'use' block (try-with-resources)
        try (RimeConfig config = schemaConfig) {

            // 1. Load switches list
            // Define the RimeConfigAction for loading a single Switch object
            RimeConfig.RimeConfigAction<Switch> switchLoader = (rc, path) -> {
                // Get the nested properties. Null checks convert Kotlin's ? : defaults.
                String switchName = rc.getString(path + "/name");
                if (switchName == null) switchName = "";

                // For nested lists (options, states), we need another action to get Strings
                RimeConfig.RimeConfigAction<String> stringAction = (innerRc, innerPath) -> innerRc.getString(innerPath);

                List<String> options = rc.getList(path + "/options", stringAction);
                Integer resetInt = rc.getInt(path + "/reset");
                int reset = (resetInt != null) ? resetInt : 0;
                List<String> states = rc.getList(path + "/states", stringAction);

                return new Switch(switchName, options, reset, states);
            };

            this.switches = config.getList("switches", switchLoader);

            // 2. Load alphabet string
            String alpha = config.getString("speller/alphabet");
            this.alphabet = (alpha != null) ? alpha : "";

        } catch (Exception e) {
            // Handle AutoCloseable exception if RimeConfig.close() fails or during construction/loading
            // For simplicity, we initialize to defaults on fatal failure.
            // In a real app, this should throw/log more aggressively.
            System.err.println("Error loading RimeSchema for ID: " + schemaId + ". " + e.getMessage());
            this.switches = Collections.emptyList();
            this.alphabet = "";
        }
    }

    // --- Public Getters ---

    public String getSchemaId() {
        return schemaId;
    }

    public List<Switch> getSwitches() {
        return switches;
    }

    public String getAlphabet() {
        return alphabet;
    }
}
