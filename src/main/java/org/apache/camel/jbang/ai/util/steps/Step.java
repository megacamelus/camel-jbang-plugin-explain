package org.apache.camel.jbang.ai.util.steps;

public class Step {
    public static final Step CONTINUE = new Step(true);
    public static final Step SKIP = new Step(false);

    private final boolean next;

    public Step(boolean next) {
        this.next = next;
    }

    public boolean next() {
        return next;
    }
}
