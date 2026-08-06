package com.github.epsilon.accounts;

import com.google.gson.annotations.SerializedName;

public class TexturesJson {

    @SerializedName("textures")
    public Textures textures;

    public static class Textures {
        @SerializedName("SKIN")
        public Texture SKIN;
    }

    public static class Texture {
        @SerializedName("url")
        public String url;
    }

}
