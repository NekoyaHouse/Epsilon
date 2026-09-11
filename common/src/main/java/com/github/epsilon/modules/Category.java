package com.github.epsilon.modules;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.graphics.text.IconChars;

public enum Category {

    COMBAT(IconChars.SWORDS, "combat"),
    PLAYER(IconChars.PERSON, "player"),
    MOVEMENT(IconChars.DIRECTIONS_RUN, "movement"),
    RENDER(IconChars.BRUSH, "render");

    public final String icon;
    private final String name;
    private final TranslateComponent translateComponent;

    Category(String icon, String name) {
        this.icon = icon;
        this.name = name;
        translateComponent = EpsilonTranslateComponent.create("categories", name);
    }

    public String getName() {
        return translateComponent.getTranslatedName();
    }

    @Override
    public String toString() {
        return name;
    }

}
