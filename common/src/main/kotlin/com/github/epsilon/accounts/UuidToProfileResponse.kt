package com.github.epsilon.accounts

import com.google.gson.annotations.SerializedName

class UuidToProfileResponse {

    @field:SerializedName("properties")
    var properties: Array<Property>? = null

    fun getPropertyValue(name: String): String? {
        for (property in properties ?: return null) {
            if (name == property.name) return property.value
        }
        return null
    }

    class Property {
        @field:SerializedName("name")
        var name: String? = null

        @field:SerializedName("value")
        var value: String? = null
    }

}
