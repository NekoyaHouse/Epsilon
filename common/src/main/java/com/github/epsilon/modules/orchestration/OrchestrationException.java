package com.github.epsilon.modules.orchestration;

public class OrchestrationException extends RuntimeException {
    public OrchestrationException(String message) { super(message); }
    public OrchestrationException(String message, Throwable cause) { super(message, cause); }
}
