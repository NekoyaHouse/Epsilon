#ifdef EPSILON_RETAINED
layout(location = UI_PROPERTY_LOCATION) in vec4 UiRow0;
layout(location = UI_PROPERTY_ROW1_LOCATION) in vec4 UiRow1;
layout(location = UI_PROPERTY_TINT_LOCATION) in vec4 UiTint;
layout(location = 7) flat out vec4 epsilonTint;
vec4 epsilonPosition(vec4 p) {
    return vec4(dot(UiRow0.xy, p.xy) + UiRow0.z, dot(UiRow1.xy, p.xy) + UiRow1.z, p.zw);
}
#else
vec4 epsilonPosition(vec4 p) { return p; }
#endif
