package com.github.epsilon.accounts;

public class UuidToProfileResponse {

    public Property[] properties;

    public String getPropertyValue(String name) {
        for (Property property : properties) {
            if (property.name.equals(name)) return property.value;
        }

        return null;
    }

    public static class Property {
        public String name;
        public String value;
    }

}
