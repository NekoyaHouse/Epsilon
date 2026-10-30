package com.github.epsilon.accounts

import com.google.gson.annotations.SerializedName

class TexturesJson {

    @field:SerializedName("textures")
    var textures: Textures? = null

    class Textures {
        @field:SerializedName("SKIN")
        var SKIN: Texture? = null
    }

    class Texture {
        @field:SerializedName("url")
        var url: String? = null
    }

}
