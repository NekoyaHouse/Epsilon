package com.github.epsilon.accounts;

public record AuthResult(boolean success, AuthError error, String detail) {

    private static final AuthResult OK = new AuthResult(true, null, null);

    public static AuthResult ok() {
        return OK;
    }

    public static AuthResult failure(AuthError error) {
        return new AuthResult(false, error == null ? AuthError.UNKNOWN : error, null);
    }

    public static AuthResult failure(AuthError error, String detail) {
        return new AuthResult(false, error == null ? AuthError.UNKNOWN : error, detail);
    }

    public String getMessage() {
        return error == null ? "" : error.getMessage();
    }

}
