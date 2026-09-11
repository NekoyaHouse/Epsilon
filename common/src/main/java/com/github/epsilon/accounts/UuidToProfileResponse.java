package com.github.epsilon.accounts;

import com.google.gson.annotations.SerializedName;

public class UuidToProfileResponse {

    @SerializedName("properties")
    public Property[] properties;

    public String getPropertyValue(String name) {
        for (Property property : properties) {
            if (name.equals(property.name)) return property.value;
        }
        return null;
    }

    public static class Property {
        @SerializedName("name")
        public String name;

        @SerializedName("value")
        public String value;
    }

}
