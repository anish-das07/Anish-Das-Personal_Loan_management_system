package com.example.plms.core.workflow;

public class IllegalStateTransitionException extends RuntimeException {

    private final transient Object from;
    private final transient Object to;

    public IllegalStateTransitionException(Object from, Object to) {
        super("Transition " + from + " -> " + to + " is not allowed");
        this.from = from;
        this.to = to;
    }

    public Object from() {
        return from;
    }

    public Object to() {
        return to;
    }
}
