package com.litukang.wordsnap.solver;

import java.util.ArrayList;
import java.util.List;

/** 一道题：一句题干 + 若干选项。 */
public class Question {

    public static class Option {
        public final String label;
        public final String text;

        public Option(String label, String text) {
            this.label = label;
            this.text = text;
        }

        @Override
        public String toString() {
            return label + ". " + text;
        }
    }

    public final String stem;
    public final List<Option> options;
    public final String raw;

    public Question(String stem, List<Option> options, String raw) {
        this.stem = stem == null ? "" : stem.trim();
        this.options = options == null ? new ArrayList<>() : options;
        this.raw = raw == null ? "" : raw;
    }

    public boolean valid() {
        return !stem.isEmpty() && options.size() >= 2;
    }

    public int size() {
        return options.size();
    }

    public String optionText(int i) {
        return (i >= 0 && i < options.size()) ? options.get(i).text : "";
    }

    public String optionLabel(int i) {
        return (i >= 0 && i < options.size()) ? options.get(i).label : "";
    }
}
