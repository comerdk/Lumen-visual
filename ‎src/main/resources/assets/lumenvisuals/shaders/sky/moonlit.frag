#version 330 core

/**
 * K3D MOONLIT NIGHT — интегрирован в Lumen Visuals.
 * Рисуется только там, где depth ≈ 1 (настоящее небо), блоки не перекрывает.
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
uniform vec4  uCameraQuat;   // xyz=qv, w=qw
uniform sampler2D uDepth;

out vec4 fragColor;

const float PI = 3.14159265359;
const float TWO_PI = 6.28318530718;

float saturate(float x) { return clamp(x, 0.0, 1.0); }
vec3  saturate(vec3 x)  { return clamp(x, vec3(0.0), vec3(1.0)); }

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
    vec3 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);

    float c000 = hash31(i);
    float c100 = hash31(i + vec3(1.0, 0.0, 0.0));
    float c010 = hash31(i + vec3(0.0, 1.0, 0.0));
    float c110 = hash31(i + vec3(1.0, 1.0, 1.0));
    float c001 = hash31(i + vec3(0.0, 0.0, 1.0));
    float c101 = hash31(i + vec3(1.0, 0.0, 1.0));
    float c011 = hash31(i + vec3(0.0, 1.0, 1.0));
    float c111 = hash31(i + vec3(1.0, 1.0, 1.0));

    // fix: c110 should be (1,1,0)
    c110 = hash31(i + vec3(1.0, 1.0, 0.0));

    vec2 x0 = mix(vec2(c000, c001), vec2(c100, c101), u.x);
    vec2 x1 = mix(vec2(c010, c011), vec2(c110, c111), u.x);
    vec2 y  = mix(x0, x1, u.y);
    return mix(y.x, y.y, u.z);
}

float fbm3D(vec3 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    for (int i = 0; i < octaves; i++) {
        value += noise3D(p * frequency) * amplitude;
        frequency *= 2.07;
        amplitude *= 0.5;
    }
    return value;
}

float fbm3DHQ(vec3 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    for (int i = 0; i < octaves; i++) {
        float s1 = noise3D(p * frequency);
        float s2 = noise3D(p * frequency + vec3(0.01));
        value += (s1 + s2) * 0.5 * amplitude;
        frequency *= 2.07;
        amplitude *= 0.5;
    }
    return value;
}

float ridged3DHQ(vec3 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    for (int i = 0; i < octaves; i++) {
        float n1 = noise3D(p * frequency);
        float n2 = noise3D(p * frequency + vec3(0.01));
        float n = (n1 + n2) * 0.5;
        n = 1.0 - abs(n * 2.0 - 1.0);
        n *= n;
        value += n * amplitude;
        frequency *= 2.05;
        amplitude *= 0.5;
    }
    return value;
}

vec3 starField(vec3 dir, float intensity, float scale) {
    float longitude = atan(dir.z, dir.x);
    float latitude  = asin(clamp(dir.y, -1.0, 1.0));
    vec2 uv    = vec2(longitude / TWO_PI, latitude / PI) * scale;
    vec2 cell  = floor(uv);
    vec2 local = fract(uv);
    float rnd  = hash21(cell);
    float starMask = smoothstep(0.992, 0.9998, rnd);
    vec2 starPos = vec2(hash21(cell + 17.13), hash21(cell + 41.91));
    float distanceToStar = length(local - starPos);
    float radius = mix(0.020, 0.08, hash21(cell + 71.2));
    float star = 1.0 - smoothstep(0.0, radius, distanceToStar);
    float brightness = mix(0.4, 1.5, hash21(cell + 13.7));
    float twinkle = sin(hash21(cell + 99.0) * 100.0 + hash21(cell) * 20.0) * 0.15 + 0.85;
    vec3 white = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.85, 0.65), hash21(cell + 83.0));
    float core = smoothstep(0.5, 0.0, distanceToStar / max(radius, 1e-4));
    return white * starMask * star * brightness * intensity * twinkle
         + white * core * starMask * brightness * intensity * 0.5;
}

float galaxyBand(vec3 dir) {
    vec3 galacticNormal = normalize(vec3(-0.15, 0.83, 0.52));
    return exp(-abs(dot(dir, galacticNormal)) * 5.0);
}

vec3 horizonGlow(vec3 dir, vec3 color, float intensity, float falloff) {
    float horizon = pow(saturate(1.0 - abs(dir.y)), falloff);
    return color * horizon * intensity;
}

float sunGlow(vec3 dir, vec3 sunDirection, float size) {
    float d = dot(dir, normalize(sunDirection));
    float angle = acos(clamp(d, -1.0, 1.0));
    return exp(-angle * angle / size);
}

float moonDisc(vec3 dir, vec3 moonDirection, float size) {
    float d = dot(dir, normalize(moonDirection));
    return smoothstep(1.0 - size, 1.0 - size * 0.12, d);
}

vec3 skyGradient(float y, vec3 horizon, vec3 middle, vec3 zenith) {
    y = saturate(y * 0.5 + 0.5);
    if (y < 0.5) return mix(horizon, middle, smoothstep(0.0, 0.5, y));
    return mix(middle, zenith, smoothstep(0.5, 1.0, y));
}

vec3 calcRayDir() {
    vec2 uv = gl_FragCoord.xy / uResolution.xy;
    vec2 sp = uv * 2.0 - 1.0;
    float aspect = uResolution.x / max(uResolution.y, 1.0);
    float tanV = tan(radians(max(uFov, 30.0)) * 0.5);
    vec3 ray = normalize(vec3(sp.x * tanV * aspect, sp.y * tanV, -1.0));

    vec3 qv = uCameraQuat.xyz;
    float qw = uCameraQuat.w;
    mat3 rot = mat3(
        1.0 - 2.0 * (qv.y * qv.y + qv.z * qv.z),
        2.0 * (qv.x * qv.y + qv.z * qw),
        2.0 * (qv.x * qv.z - qv.y * qw),

        2.0 * (qv.x * qv.y - qv.z * qw),
        1.0 - 2.0 * (qv.x * qv.x + qv.z * qv.z),
        2.0 * (qv.y * qv.z + qv.x * qw),

        2.0 * (qv.x * qv.z + qv.y * qw),
        2.0 * (qv.y * qv.z - qv.x * qw),
        1.0 - 2.0 * (qv.x * qv.x + qv.y * qv.y)
    );
    return rot * ray;
}

vec3 presetMoonlit(vec3 dir, float time, int quality) {
    vec3 zenith  = vec3(0.008, 0.012, 0.06);
    vec3 middle  = vec3(0.025, 0.035, 0.15);
    vec3 horizon = vec3(0.30, 0.15, 0.35);
    vec3 color = skyGradient(dir.y, horizon, middle, zenith);

    color += horizonGlow(dir, vec3(0.30, 0.08, 0.40), 1.0, 3.2);
    color += horizonGlow(dir, vec3(0.15, 0.10, 0.30), 0.5, 5.5);

    vec3 moonDirection = normalize(vec3(0.15, 0.38, 0.91));
    float moon = moonDisc(dir, moonDirection, 0.075);
    float moonGlow = sunGlow(dir, moonDirection, 0.055);
    color += vec3(0.40, 0.55, 1.0) * moonGlow * 3.0;
    color += vec3(0.90, 0.98, 1.0) * moon * 8.0;

    float moonAtmos1 = sunGlow(dir, moonDirection, 0.12);
    float moonAtmos2 = sunGlow(dir, moonDirection, 0.22);
    color += vec3(0.35, 0.50, 0.85) * moonAtmos1 * 1.5;
    color += vec3(0.25, 0.40, 0.70) * moonAtmos2 * 0.8;

    float corona = pow(sunGlow(dir, moonDirection, 0.18), 3.0) * (1.0 - moon);
    color += vec3(0.50, 0.65, 0.95) * corona * 1.2;

    float animTime = time * 2.5;
    int oct = quality >= 2 ? 4 : (quality >= 1 ? 3 : 2);

    vec3 nebP = dir * 4.5;
    float nebBase   = fbm3DHQ(nebP * 0.7 + vec3(animTime * 0.015, animTime * 0.008, 0.0), oct);
    float nebFlow   = ridged3DHQ(nebP * 1.2 - vec3(animTime * 0.022, animTime * 0.012, 0.0), oct);
    float nebDetail = fbm3D(nebP * 2.0 + vec3(animTime * 0.018, -animTime * 0.01, 0.0), max(oct - 1, 1));
    float nebula = smoothstep(0.25, 0.80, nebBase * 0.6 + nebFlow * 0.3 + nebDetail * 0.2);

    vec3 nebDeep   = vec3(0.04, 0.02, 0.15);
    vec3 nebMid    = vec3(0.12, 0.25, 0.65);
    vec3 nebBright = vec3(0.25, 0.50, 0.95);
    vec3 nebulaColor = nebula < 0.5
        ? mix(nebDeep, nebMid, nebula / 0.5)
        : mix(nebMid, nebBright, (nebula - 0.5) / 0.5);
    color += nebulaColor * nebula * 0.8 * uNebula;

    float starIntensity = uStars;
    color += starField(dir, starIntensity * 3.0, 145.0);
    color += starField(dir, starIntensity * 2.0, 350.0);
    if (quality >= 1) {
        color += starField(dir, starIntensity * 1.2, 680.0);
        color += starField(dir, starIntensity * 0.7, 1200.0);
    }
    if (quality >= 2) {
        color += starField(dir, starIntensity * 0.4, 1900.0);
    }

    float milkyWay = pow(galaxyBand(dir), 1.3);
    float mwDetail1 = fbm3D(dir * 25.0 + vec3(animTime * 0.005, 0.0, 0.0), 3);
    float mwDetail2 = fbm3D(dir * 12.0 - vec3(animTime * 0.003, 0.0, 0.0), 3);
    float mwStars = mwDetail1 * 0.6 + mwDetail2 * 0.4;
    color += vec3(0.55, 0.65, 0.90) * milkyWay * mwStars * starIntensity * 0.8;
    color += vec3(0.70, 0.80, 1.0) * pow(milkyWay, 2.0) * starIntensity * 0.5;

    vec3 cloudP = dir * 10.0;
    cloudP.x += animTime * 0.010;
    cloudP.y += sin(animTime * 0.006) * 0.4;
    cloudP.z += cos(animTime * 0.004) * 0.2;
    float cloudBase  = fbm3DHQ(cloudP * 0.6, oct);
    float cloudFlow  = ridged3DHQ(cloudP * 1.3 + vec3(animTime * 0.015, 0.0, 0.0), oct);
    float cloudWisps = fbm3D(cloudP * 2.2 - vec3(animTime * 0.02, 0.0, 0.0), max(oct - 1, 1));
    float clouds = smoothstep(0.45, 0.75, cloudBase * 0.55 + cloudFlow * 0.30 + cloudWisps * 0.25);
    float horizonMask = 1.0 - smoothstep(0.12, 0.90, abs(dir.y));
    clouds *= mix(0.30, 1.0, horizonMask);

    float moonLight = saturate(sunGlow(dir, moonDirection, 0.60));
    vec3 cloudDark   = vec3(0.05, 0.07, 0.12);
    vec3 cloudLit    = vec3(0.20, 0.25, 0.40);
    vec3 cloudColor  = mix(cloudDark, cloudLit, moonLight);
    float cloudEdge  = (1.0 - clouds) * clouds * moonLight;
    cloudColor += vec3(0.60, 0.70, 0.90) * cloudEdge * 1.5;
    color = mix(color, cloudColor, clouds * uClouds * 0.65);

    float shimmer = pow(fbm3D(dir * 18.0 + vec3(animTime * 0.05, animTime * 0.03, 0.0), 3), 3.0);
    color += vec3(0.70, 0.80, 1.0) * shimmer * starIntensity * 0.3;
    float nightAura = pow(1.0 - abs(dir.y), 4.0);
    color += vec3(0.15, 0.10, 0.30) * nightAura * 0.2;

    return color;
}

vec3 acesTonemap(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

bool skyHidden() {
    vec2 uv = gl_FragCoord.xy / uResolution.xy;
    vec2 margin = vec2(1.5 / max(uResolution.x, 1.0), 1.5 / max(uResolution.y, 1.0));
    float far = texture(uDepth, uv).r;
    far = max(far, texture(uDepth, uv + vec2( margin.x, -margin.y)).r);
    far = max(far, texture(uDepth, uv + vec2(-margin.x,  margin.y)).r);
    far = max(far, texture(uDepth, uv + vec2( margin.x,  margin.y)).r);
    // в MC depth≈1.0 на небе (в зависимости от reverse-Z может быть ≈0 — проверяем оба края)
    return far > 0.0005 && far < 0.9995;
}

void main() {
    if (skyHidden()) {
        discard;
    }

    vec3 dir = normalize(calcRayDir());
    float time = uTime * max(uSpeed, 0.05);
    int quality = clamp(int(round(uQuality)), 0, 2);

    vec3 color = presetMoonlit(dir, time, quality);
    color *= clamp(uBrightness, 0.0, 10.0);

    if (any(isnan(color)) || any(isinf(color))) color = vec3(0.0);
    color = acesTonemap(max(color, vec3(0.0)));
    color = smoothstep(0.0, 1.0, color);

    fragColor = vec4(color, 1.0);
}
