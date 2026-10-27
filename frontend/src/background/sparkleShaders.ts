/** GLSL for the sparkle field: one draw call, one point sprite per particle. */

export const VERTEX_SHADER = /* glsl */ `
precision highp float;

attribute vec3 a_pos;
attribute vec4 a_seed;   // size, phase, twinkle rate, colour mix
attribute float a_kind;  // 0 dust, 1 star, 2 bokeh

uniform float u_time;
uniform vec2  u_mouse;     // eased pointer offset, roughly -1..1
uniform vec2  u_view;      // canvas size in device pixels
uniform float u_dpr;
uniform float u_intensity; // 0..1
uniform float u_focus;     // focal depth in 0..1 (0.5 = middle of volume)

varying float v_alpha;
varying float v_kind;
varying float v_blur;
varying vec3  v_color;
varying float v_flash;

const vec3 C_WHITE  = vec3(0.86, 0.91, 1.00);
const vec3 C_CYAN   = vec3(0.45, 0.88, 1.00);
const vec3 C_VIOLET = vec3(0.60, 0.48, 1.00);
const vec3 C_INDIGO = vec3(0.36, 0.42, 0.98);

void main() {
  float size = a_seed.x;
  float phase = a_seed.y;
  float rate = a_seed.z;
  float mixv = a_seed.w;

  vec3 p = a_pos;

  // Gentle drift: slow upward float and sideways sway; deeper particles move less (parallax).
  float depth01 = p.z * 0.5 + 0.5;
  float speed = mix(0.010, 0.032, 1.0 - depth01);
  p.y = mod(p.y + 1.0 + u_time * speed, 2.0) - 1.0;
  p.x += sin(u_time * 0.07 * rate + phase) * 0.035;

  // Slow whole-field rotation about Y with a slight X tilt, for true 3D depth.
  float ay = u_time * 0.018;
  float ax = sin(u_time * 0.011) * 0.08;
  float cy = cos(ay), sy = sin(ay);
  float cx = cos(ax), sx = sin(ax);
  p = vec3(p.x * cy + p.z * sy, p.y, -p.x * sy + p.z * cy);
  p = vec3(p.x, p.y * cx - p.z * sx, p.y * sx + p.z * cx);

  // Pointer parallax: nearer particles shift more.
  float near = 1.0 - (p.z * 0.5 + 0.5);
  p.xy += u_mouse * (0.02 + near * 0.07) * vec2(1.0, -1.0);

  // Perspective projection (camera sits at z = -2.6 looking along +z).
  float camZ = 2.6;
  float w = camZ + p.z;                       // distance from camera, ~1.6 .. 3.6
  float persp = camZ / max(w, 0.6);
  float aspect = u_view.x / u_view.y;
  vec2 ndc = vec2(p.x * persp / max(aspect * 0.62, 0.62), p.y * persp * 0.9);
  // keep a wide field on portrait screens
  if (aspect < 1.0) ndc.x = p.x * persp * 0.9;
  gl_Position = vec4(ndc, 0.0, 1.0);

  // Depth of field: blur grows with distance from the focal plane.
  float coc = clamp(abs(depth01 - u_focus) * 2.0, 0.0, 1.0);
  v_blur = coc;

  float base = a_kind > 1.5 ? 70.0 : (a_kind > 0.5 ? 15.0 : 5.0);
  float px = (base * (0.55 + size)) * persp * u_dpr * (1.0 + coc * 0.9);
  gl_PointSize = clamp(px, 1.5, a_kind > 1.5 ? 220.0 * u_dpr : 64.0 * u_dpr);

  // Twinkle: a slow shimmer plus an occasional, soft, delicate pulse (never a hard flash).
  float shimmer = 0.78 + 0.22 * sin(u_time * rate * 0.9 + phase);
  float pulse = pow(max(0.0, sin(u_time * rate * 0.22 + phase * 3.1)), 18.0);
  v_flash = pulse * (a_kind > 0.5 ? 1.0 : 0.4);

  float depthFade = mix(0.35, 1.0, near);
  float kindAlpha = a_kind > 1.5 ? 0.11 : (a_kind > 0.5 ? 0.85 : 0.52);
  v_alpha = kindAlpha * shimmer * depthFade * (1.0 - coc * 0.45) * u_intensity;

  // Palette: cool white dominant, cyan highlights, violet/indigo body.
  vec3 col = mixv < 0.38 ? C_WHITE : (mixv < 0.50 ? C_CYAN : (mixv < 0.78 ? C_VIOLET : C_INDIGO));
  if (a_kind > 1.5) col = mixv < 0.5 ? C_INDIGO : C_VIOLET;
  v_color = col;
  v_kind = a_kind;
}
`;

export const FRAGMENT_SHADER = /* glsl */ `
precision mediump float;

varying float v_alpha;
varying float v_kind;
varying float v_blur;
varying vec3  v_color;
varying float v_flash;

void main() {
  vec2 c = gl_PointCoord * 2.0 - 1.0;
  float r = length(c);
  if (r > 1.0) discard;

  // Soft glow core. Out-of-focus sprites get a wide, flat disc (bokeh); in-focus ones a tight core.
  float softness = mix(2.6, 0.9, v_blur);
  float core = exp(-r * r * softness * 3.2);
  float disc = 1.0 - smoothstep(0.55, 1.0, r);
  float glow = v_kind > 1.5 ? disc * 0.55 + core * 0.2 : core;

  // Fine four-point diffraction cross on stars, brightening during a twinkle.
  float cross = 0.0;
  if (v_kind > 0.5 && v_kind < 1.5) {
    vec2 a = abs(c);
    float arm = exp(-min(a.x, a.y) * 38.0) * (1.0 - smoothstep(0.0, 1.0, max(a.x, a.y)));
    cross = arm * (0.35 + v_flash * 0.9) * (1.0 - v_blur);
  }

  float a = (glow + cross) * v_alpha * (1.0 + v_flash * 1.2);
  vec3 col = mix(v_color, vec3(1.0), clamp(v_flash * 0.5, 0.0, 0.5));
  // premultiplied alpha, additive-friendly
  gl_FragColor = vec4(col * a, a);
}
`;
