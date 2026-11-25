#version 330 core

/**
 * Moonlit Night — Lumen Visuals
 * Камера: uCamRight / uCamUp / uCamForward
 */

uniform vec2  uResolution;
uniform float uTime;
uniform float uSpeed;
uniform float uBrightness;
uniform float uStars;
uniform float uFov;
uniform float uQuality;
uniform float uNebula;
uniform float uClouds;
uniform vec3  uCamRight;
uniform vec3  uCamUp;
uniform vec3  uCamForward;
uniform sampler2D uDepth;
uniform float uHasDepth;

out vec4 fragColor;

const float PI = 3.14159265359;
const float TWO_PI = 6.28318530718;

float saturate(float x) { return clamp(x, 0.0, 1.0); }

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise3D(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float n000 = hash31(i);
    float n100 = hash31(i + vec3(1,0,0));
    float n010 = hash31(i + vec3(0,1,0));
    float n110 = hash31(i + vec3(1,1,0));
    float n001 = hash31(i + vec3(0,0,1));
    float n101 = hash31(i + vec3(1,0,1));
    float n011 = hash31(i + vec3(0,1,1));
    float n111 = hash31(i + vec3(1,1,1));
    float x00 = mix(n000, n100, u.x);
    float x10 = mix(n010, n110, u.x);
    float x01 = mix(n001, n101, u.x);
    float x11 = mix(n011, n111, u.x);
    return mix(mix(x00, x10, u.y), mix(x01, x11, u.y), u.z);
}

float fbm(vec3 p, int oct) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < oct; i++) {
        v += noise3D(p) * a;
        p = p * 2.05 + 17.1;
        a *= 0.5;
    }
    return v;
}

float ridged(vec3 p, int oct) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < oct; i++) {
        float n = 1.0 - abs(noise3D(p) * 2.0 - 1.0);
        n *= n;
        v += n * a;
        p = p * 2.03 + 9.7;
        a *= 0.5;
    }
    return v;
}

vec3 starField(vec3 dir, float intensity, float scale) {
    float lon = atan(dir.z, dir.x);
    float lat = asin(clamp(dir.y, -1.0, 1.0));
    vec2 uv = vec2(lon / TWO_PI, lat / PI) * scale;
    vec2 cell = floor(uv);
    vec2 local = fract(uv);
    float rnd = hash21(cell);
    float mask = smoothstep(0.993, 0.9997, rnd);
    vec2 pos = vec2(hash21(cell + 17.13), hash21(cell + 41.91));
    float d = length(local - pos);
    float radius = mix(0.015, 0.06, hash21(cell + 71.2));
    float star = 1.0 - smoothstep(0.0, radius, d);
    float bri = mix(0.35, 1.4, hash21(cell + 13.7));
    float tw = sin(hash21(cell + 99.0) * 40.0 + uTime * (1.5 + hash21(cell) * 3.0)) * 0.12 + 0.88;
    vec3 col = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.88, 0.7), hash21(cell + 83.0));
    float core = smoothstep(0.45, 0.0, d / max(radius, 1e-4));
    return col * mask * star * bri * intensity * tw + col * core * mask * bri * intensity * 0.4;
}

float galaxyBand(vec3 dir) {
    vec3 n = normalize(vec3(-0.15, 0.83, 0.52));
    return exp(-abs(dot(dir, n)) * 5.0);
}

vec3 horizonGlow(vec3 dir, vec3 color, float intensity, float falloff) {
    float h = pow(saturate(1.0 - abs(dir.y)), falloff);
    return color * h * intensity;
}

float glow(vec3 dir, vec3 to, float size) {
    float d = dot(dir, normalize(to));
    float ang = acos(clamp(d, -1.0, 1.0));
    return exp(-ang * ang / size);
}

float disc(vec3 dir, vec3 to, float size) {
    float d = dot(dir, normalize(to));
    return smoothstep(1.0 - size, 1.0 - size * 0.12, d);
}

vec3 skyGradient(float y) {
    y = saturate(y * 0.5 + 0.5);
    vec3 zenith  = vec3(0.008, 0.012, 0.06);
    vec3 middle  = vec3(0.025, 0.035, 0.15);
    vec3 horizon = vec3(0.22, 0.12, 0.32);
    if (y < 0.5) return mix(horizon, middle, smoothstep(0.0, 0.5, y));
    return mix(middle, zenith, smoothstep(0.5, 1.0, y));
}

vec3 calcRayDir() {
    vec2 uv = gl_FragCoord.xy / uResolution.xy;
    vec2 sp = uv * 2.0 - 1.0;
    float aspect = uResolution.x / max(uResolution.y, 1.0);
    float tanV = tan(radians(max(uFov, 30.0)) * 0.5);
    vec3 view = normalize(vec3(sp.x * tanV * aspect, sp.y * tanV, -1.0));
    return normalize(uCamRight * view.x + uCamUp * view.y + uCamForward * (-view.z));
}

bool isSkyPixel() {
    if (uHasDepth < 0.5) return true;
    vec2 uv = gl_FragCoord.xy / uResolution.xy;
    float d = texture(uDepth, uv).r;
    return d > 0.999 || d < 0.001;
}

vec3 moonlit(vec3 dir, float time, int quality) {
    vec3 color = skyGradient(dir.y);
    color += horizonGlow(dir, vec3(0.28, 0.08, 0.38), 0.9, 3.2);
    color += horizonGlow(dir, vec3(0.12, 0.08, 0.28), 0.45, 5.5);

    vec3 moonDir = normalize(vec3(0.18, 0.42, 0.88));
    float moon = disc(dir, moonDir, 0.07);
    float mg = glow(dir, moonDir, 0.05);
    color += vec3(0.40, 0.55, 1.0) * mg * 2.6;
    color += vec3(0.92, 0.97, 1.0) * moon * 7.0;
    color += vec3(0.35, 0.48, 0.85) * glow(dir, moonDir, 0.12) * 1.2;
    color += vec3(0.25, 0.38, 0.70) * glow(dir, moonDir, 0.22) * 0.7;

    float t = time * 2.2;
    int oct = quality >= 2 ? 4 : (quality >= 1 ? 3 : 2);

    vec3 np = dir * 3.8;
    float nb = fbm(np * 0.65 + vec3(t * 0.012, t * 0.007, 0.0), oct);
    float nf = ridged(np * 1.05 - vec3(t * 0.018, t * 0.01, 0.0), max(oct - 1, 1));
    float neb = smoothstep(0.35, 0.82, nb * 0.65 + nf * 0.35);
    vec3 nCol = mix(vec3(0.05, 0.03, 0.14), vec3(0.18, 0.35, 0.85), neb);
    color += nCol * neb * 0.55 * uNebula;

    float si = uStars;
    color += starField(dir, si * 2.6, 160.0);
    color += starField(dir, si * 1.7, 380.0);
    if (quality >= 1) color += starField(dir, si * 1.0, 720.0);
    if (quality >= 2) color += starField(dir, si * 0.55, 1400.0);

    float mw = pow(galaxyBand(dir), 1.35);
    float mwd = fbm(dir * 18.0 + vec3(t * 0.004, 0.0, 0.0), 3);
    color += vec3(0.55, 0.65, 0.9) * mw * mwd * si * 0.55;

    vec3 cp = dir * 8.5;
    cp.x += t * 0.008;
    float cb = fbm(cp * 0.55, oct);
    float clouds = smoothstep(0.52, 0.78, cb);
    clouds *= 1.0 - smoothstep(0.15, 0.85, abs(dir.y));
    float ml = saturate(glow(dir, moonDir, 0.55));
    vec3 cCol = mix(vec3(0.04, 0.05, 0.09), vec3(0.18, 0.22, 0.38), ml);
    color = mix(color, cCol, clouds * uClouds * 0.5);

    float aura = pow(1.0 - abs(dir.y), 4.0);
    color += vec3(0.12, 0.08, 0.25) * aura * 0.18;
    return color;
}

vec3 aces(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    if (!isSkyPixel()) discard;

    vec3 dir = normalize(calcRayDir());
    float time = uTime * max(uSpeed, 0.05);
    int quality = clamp(int(round(uQuality)), 0, 2);

    vec3 color = moonlit(dir, time, quality);
    color *= clamp(uBrightness, 0.15, 6.0);

    if (any(isnan(color)) || any(isinf(color))) color = vec3(0.01, 0.015, 0.04);
    color = aces(max(color, vec3(0.0)));
    color = smoothstep(0.0, 1.0, color);

    fragColor = vec4(color, 1.0);
}
