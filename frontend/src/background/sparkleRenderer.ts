import { FRAGMENT_SHADER, VERTEX_SHADER } from './sparkleShaders';
import { FLOATS_PER_PARTICLE, QualityProfile, generateParticles } from './sparkleConfig';

const STATIC_FRAME_TIME = 42; // seconds into the animation used for the reduced-motion still frame

/**
 * Dependency-free WebGL renderer for the sparkle field.
 *  - One vertex buffer, one program, one draw call per frame.
 *  - No React state updates in the loop; everything lives in this class.
 *  - Pauses when the tab is hidden, honours an fps cap, and degrades itself if frames are slow.
 *  - dispose() releases every GL resource and listener.
 */
export class SparkleRenderer {
  /** Returns null when WebGL is unavailable or shader compilation fails (caller shows the CSS fallback). */
  static create(canvas: HTMLCanvasElement, profile: QualityProfile): SparkleRenderer | null {
    let gl: WebGLRenderingContext | null = null;
    try {
      gl = canvas.getContext('webgl', {
        alpha: true,
        antialias: false,
        depth: false,
        stencil: false,
        premultipliedAlpha: true,
        powerPreference: 'low-power',
        preserveDrawingBuffer: false,
      });
    } catch {
      gl = null;
    }
    if (!gl) return null;
    const renderer = new SparkleRenderer(canvas, gl, profile);
    if (!renderer.initGL()) {
      renderer.dispose();
      return null;
    }
    return renderer;
  }

  private program: WebGLProgram | null = null;
  private buffer: WebGLBuffer | null = null;
  private uniforms: Record<string, WebGLUniformLocation | null> = {};
  private attribs = { pos: -1, seed: -1, kind: -1 };

  private data: Float32Array;
  private drawCount: number;
  private dpr: number;

  private raf = 0;
  private running = false;
  private disposed = false;
  private contextLost = false;
  private lastFrame = 0;
  private time = 0;

  private mouse = { x: 0, y: 0 };
  private mouseTarget = { x: 0, y: 0 };
  private intensity = 0;
  private intensityTarget = 0.75;
  private width = 1;
  private height = 1;
  private resizeTimer = 0;

  // adaptive quality sampling
  private sampleFrames = 0;
  private sampleTime = 0;
  private degraded = false;

  private constructor(
    private canvas: HTMLCanvasElement,
    private gl: WebGLRenderingContext,
    private profile: QualityProfile,
  ) {
    this.data = generateParticles(profile.count);
    this.drawCount = profile.count;
    this.dpr = Math.min(window.devicePixelRatio || 1, profile.dprCap);

    canvas.addEventListener('webglcontextlost', this.onContextLost, false);
    canvas.addEventListener('webglcontextrestored', this.onContextRestored, false);
    window.addEventListener('resize', this.onResize, { passive: true });
    document.addEventListener('visibilitychange', this.onVisibility);
    this.applyProfileListeners();
    this.resizeNow();
  }

  /* ------------------------------------------------------------------ public API */

  setIntensity(v: number): void {
    this.intensityTarget = Math.max(0, Math.min(1, v));
    if (!this.profile.animate) {
      this.intensity = this.intensityTarget;
      this.renderStill();
    }
  }

  /** Switch between animated and static rendering (prefers-reduced-motion changes at runtime). */
  setProfile(profile: QualityProfile): void {
    if (this.disposed) return;
    const countChanged = profile.count !== this.profile.count;
    this.profile = profile;
    this.dpr = Math.min(window.devicePixelRatio || 1, profile.dprCap);
    if (countChanged) {
      this.data = generateParticles(profile.count);
      this.drawCount = this.degraded ? Math.round(profile.count * 0.6) : profile.count;
      this.uploadData();
    }
    this.applyProfileListeners();
    this.resizeNow();
    if (profile.animate) {
      this.start();
    } else {
      this.stop();
      this.intensity = this.intensityTarget;
      this.renderStill();
    }
  }

  start(): void {
    if (this.disposed || this.running || !this.profile.animate || this.contextLost) return;
    if (document.hidden) return;
    this.running = true;
    this.lastFrame = 0;
    this.raf = requestAnimationFrame(this.tick);
  }

  stop(): void {
    this.running = false;
    if (this.raf) cancelAnimationFrame(this.raf);
    this.raf = 0;
  }

  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    this.stop();
    window.clearTimeout(this.resizeTimer);
    this.canvas.removeEventListener('webglcontextlost', this.onContextLost, false);
    this.canvas.removeEventListener('webglcontextrestored', this.onContextRestored, false);
    window.removeEventListener('resize', this.onResize);
    window.removeEventListener('pointermove', this.onPointerMove);
    window.removeEventListener('blur', this.onBlur);
    document.removeEventListener('visibilitychange', this.onVisibility);
    this.releaseGL();
    try {
      this.gl.getExtension('WEBGL_lose_context')?.loseContext();
    } catch {
      /* context already gone */
    }
  }

  /* ------------------------------------------------------------------ GL setup */

  private initGL(): boolean {
    const gl = this.gl;
    const vs = this.compile(gl.VERTEX_SHADER, VERTEX_SHADER);
    const fs = this.compile(gl.FRAGMENT_SHADER, FRAGMENT_SHADER);
    if (!vs || !fs) return false;
    const program = gl.createProgram();
    if (!program) return false;
    gl.attachShader(program, vs);
    gl.attachShader(program, fs);
    gl.linkProgram(program);
    // Shaders can be released once linked.
    gl.deleteShader(vs);
    gl.deleteShader(fs);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      gl.deleteProgram(program);
      return false;
    }
    this.program = program;
    this.attribs = {
      pos: gl.getAttribLocation(program, 'a_pos'),
      seed: gl.getAttribLocation(program, 'a_seed'),
      kind: gl.getAttribLocation(program, 'a_kind'),
    };
    for (const name of ['u_time', 'u_mouse', 'u_view', 'u_dpr', 'u_intensity', 'u_focus']) {
      this.uniforms[name] = gl.getUniformLocation(program, name);
    }
    this.buffer = gl.createBuffer();
    this.uploadData();

    gl.disable(gl.DEPTH_TEST);
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.ONE, gl.ONE); // additive light on a transparent canvas
    gl.clearColor(0, 0, 0, 0);
    return true;
  }

  private compile(type: number, source: string): WebGLShader | null {
    const gl = this.gl;
    const shader = gl.createShader(type);
    if (!shader) return null;
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
      gl.deleteShader(shader);
      return null;
    }
    return shader;
  }

  private uploadData(): void {
    if (!this.buffer) return;
    const gl = this.gl;
    gl.bindBuffer(gl.ARRAY_BUFFER, this.buffer);
    gl.bufferData(gl.ARRAY_BUFFER, this.data, gl.STATIC_DRAW);
  }

  private releaseGL(): void {
    const gl = this.gl;
    try {
      if (this.buffer) gl.deleteBuffer(this.buffer);
      if (this.program) gl.deleteProgram(this.program);
    } catch {
      /* ignore: context may already be lost */
    }
    this.buffer = null;
    this.program = null;
  }

  /* ------------------------------------------------------------------ events */

  private applyProfileListeners(): void {
    window.removeEventListener('pointermove', this.onPointerMove);
    window.removeEventListener('blur', this.onBlur);
    if (this.profile.parallax && this.profile.animate) {
      window.addEventListener('pointermove', this.onPointerMove, { passive: true });
      window.addEventListener('blur', this.onBlur);
    } else {
      this.mouseTarget = { x: 0, y: 0 };
      this.mouse = { x: 0, y: 0 };
    }
  }

  private onPointerMove = (e: PointerEvent): void => {
    if (e.pointerType === 'touch') return;
    this.mouseTarget.x = (e.clientX / Math.max(1, window.innerWidth)) * 2 - 1;
    this.mouseTarget.y = (e.clientY / Math.max(1, window.innerHeight)) * 2 - 1;
  };

  private onBlur = (): void => {
    this.mouseTarget.x = 0;
    this.mouseTarget.y = 0;
  };

  private onVisibility = (): void => {
    if (document.hidden) this.stop();
    else if (this.profile.animate) this.start();
  };

  private onResize = (): void => {
    window.clearTimeout(this.resizeTimer);
    this.resizeTimer = window.setTimeout(() => {
      this.resizeNow();
      if (!this.profile.animate) this.renderStill();
    }, 120);
  };

  private onContextLost = (e: Event): void => {
    e.preventDefault();
    this.contextLost = true;
    this.stop();
  };

  private onContextRestored = (): void => {
    this.contextLost = false;
    this.program = null;
    this.buffer = null;
    if (!this.initGL()) return;
    this.resizeNow();
    if (this.profile.animate) this.start();
    else this.renderStill();
  };

  private resizeNow(): void {
    const w = Math.max(1, Math.floor(this.canvas.clientWidth * this.dpr));
    const h = Math.max(1, Math.floor(this.canvas.clientHeight * this.dpr));
    if (w !== this.canvas.width || h !== this.canvas.height) {
      this.canvas.width = w;
      this.canvas.height = h;
    }
    this.width = w;
    this.height = h;
    if (!this.contextLost) this.gl.viewport(0, 0, w, h);
  }

  /* ------------------------------------------------------------------ drawing */

  private tick = (now: number): void => {
    if (!this.running || this.disposed) return;
    this.raf = requestAnimationFrame(this.tick);

    const minDelta = this.profile.fpsCap > 0 ? 1000 / this.profile.fpsCap - 2 : 0;
    if (this.lastFrame && now - this.lastFrame < minDelta) return;
    const dt = this.lastFrame ? Math.min((now - this.lastFrame) / 1000, 0.1) : 0.016; // clamp so resuming never jumps
    this.lastFrame = now;
    this.time += dt;

    // ease pointer + intensity towards their targets (frame-rate independent)
    const k = 1 - Math.pow(0.0008, dt);
    this.mouse.x += (this.mouseTarget.x - this.mouse.x) * k;
    this.mouse.y += (this.mouseTarget.y - this.mouse.y) * k;
    this.intensity += (this.intensityTarget - this.intensity) * (1 - Math.pow(0.02, dt));

    this.draw(this.time);
    this.sampleQuality(dt);
  };

  private renderStill(): void {
    if (this.disposed || this.contextLost) return;
    this.raf = requestAnimationFrame(() => {
      this.raf = 0;
      if (!this.disposed && !this.contextLost) this.draw(STATIC_FRAME_TIME);
    });
  }

  private draw(time: number): void {
    const gl = this.gl;
    if (!this.program || !this.buffer || gl.isContextLost()) return;
    gl.useProgram(this.program);
    gl.clear(gl.COLOR_BUFFER_BIT);

    gl.bindBuffer(gl.ARRAY_BUFFER, this.buffer);
    const stride = FLOATS_PER_PARTICLE * 4;
    const { pos, seed, kind } = this.attribs;
    gl.enableVertexAttribArray(pos);
    gl.vertexAttribPointer(pos, 3, gl.FLOAT, false, stride, 0);
    gl.enableVertexAttribArray(seed);
    gl.vertexAttribPointer(seed, 4, gl.FLOAT, false, stride, 12);
    gl.enableVertexAttribArray(kind);
    gl.vertexAttribPointer(kind, 1, gl.FLOAT, false, stride, 28);

    gl.uniform1f(this.uniforms.u_time, time);
    gl.uniform2f(this.uniforms.u_mouse, this.mouse.x, this.mouse.y);
    gl.uniform2f(this.uniforms.u_view, this.width, this.height);
    gl.uniform1f(this.uniforms.u_dpr, this.dpr);
    gl.uniform1f(this.uniforms.u_intensity, this.intensity);
    gl.uniform1f(this.uniforms.u_focus, 0.5);
    gl.drawArrays(gl.POINTS, 0, this.drawCount);
  }

  /** If the first ~100 frames are slow, shrink the particle budget and pixel ratio once. */
  private sampleQuality(dt: number): void {
    if (this.degraded || this.sampleFrames > 120) return;
    this.sampleFrames++;
    if (this.sampleFrames <= 20) return; // ignore shader warm-up
    this.sampleTime += dt;
    if (this.sampleFrames === 120) {
      const avgMs = (this.sampleTime / 100) * 1000;
      const target = this.profile.fpsCap > 0 ? 1000 / this.profile.fpsCap : 16.7;
      if (avgMs > target * 1.6) {
        this.degraded = true;
        this.drawCount = Math.round(this.profile.count * 0.6);
        this.dpr = 1;
        this.resizeNow();
      }
    }
  }
}
