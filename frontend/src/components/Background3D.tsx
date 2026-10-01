import { useEffect, useRef } from 'react';
import * as THREE from 'three';

/**
 * Full-viewport animated 3D background.
 *
 * Renders a slowly drifting field of particles plus a cluster of soft,
 * rotating wireframe polyhedra in CivicBridge's blue palette. Mounted once
 * at the app root (see App.tsx) and sits behind all page content via
 * position: fixed + negative z-index, so every page gets it "for free"
 * without each page needing its own canvas.
 *
 * Performance notes:
 * - Capped particle/shape counts keep this cheap on low-end laptops.
 * - Pauses the render loop when the tab is hidden (visibilitychange).
 * - Fully disposes the renderer, geometries and materials on unmount so
 *   React StrictMode's double-invoke in dev doesn't leak WebGL contexts.
 */
export default function Background3D() {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const scene = new THREE.Scene();

    const camera = new THREE.PerspectiveCamera(
      60,
      window.innerWidth / window.innerHeight,
      0.1,
      1000
    );
    camera.position.z = 30;

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setSize(window.innerWidth, window.innerHeight);
    container.appendChild(renderer.domElement);

    // ---- Particle field ("drifting civic data points") ----
    const PARTICLE_COUNT = 450;
    const positions = new Float32Array(PARTICLE_COUNT * 3);
    for (let i = 0; i < PARTICLE_COUNT; i++) {
      positions[i * 3] = (Math.random() - 0.5) * 120;
      positions[i * 3 + 1] = (Math.random() - 0.5) * 80;
      positions[i * 3 + 2] = (Math.random() - 0.5) * 80;
    }
    const particleGeometry = new THREE.BufferGeometry();
    particleGeometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    const particleMaterial = new THREE.PointsMaterial({
      color: 0x60a5fa,
      size: 0.35,
      transparent: true,
      opacity: 0.55,
      depthWrite: false,
    });
    const particles = new THREE.Points(particleGeometry, particleMaterial);
    scene.add(particles);

    // ---- Floating wireframe polyhedra ----
    const shapes: THREE.Mesh[] = [];
    const shapeGeometries = [
      new THREE.IcosahedronGeometry(4, 0),
      new THREE.OctahedronGeometry(3.2, 0),
      new THREE.TetrahedronGeometry(3.6, 0),
    ];
    const colors = [0x1a56db, 0x3b82f6, 0x60a5fa];

    for (let i = 0; i < 6; i++) {
      const geometry = shapeGeometries[i % shapeGeometries.length];
      const material = new THREE.MeshBasicMaterial({
        color: colors[i % colors.length],
        wireframe: true,
        transparent: true,
        opacity: 0.35,
      });
      const mesh = new THREE.Mesh(geometry, material);
      mesh.position.set(
        (Math.random() - 0.5) * 50,
        (Math.random() - 0.5) * 30,
        (Math.random() - 0.5) * 40 - 10
      );
      mesh.userData.rotationSpeed = {
        x: (Math.random() - 0.5) * 0.004,
        y: (Math.random() - 0.5) * 0.004,
      };
      scene.add(mesh);
      shapes.push(mesh);
    }

    // Subtle parallax: camera drifts slightly toward the mouse position.
    const mouse = { x: 0, y: 0 };
    function handleMouseMove(e: MouseEvent) {
      mouse.x = (e.clientX / window.innerWidth - 0.5) * 2;
      mouse.y = (e.clientY / window.innerHeight - 0.5) * 2;
    }
    window.addEventListener('mousemove', handleMouseMove);

    let animationFrameId: number;
    let isVisible = true;

    function animate() {
      if (!isVisible) return;
      animationFrameId = requestAnimationFrame(animate);

      particles.rotation.y += 0.0006;
      particles.rotation.x += 0.0002;

      shapes.forEach((mesh) => {
        mesh.rotation.x += mesh.userData.rotationSpeed.x;
        mesh.rotation.y += mesh.userData.rotationSpeed.y;
      });

      camera.position.x += (mouse.x * 4 - camera.position.x) * 0.02;
      camera.position.y += (-mouse.y * 3 - camera.position.y) * 0.02;
      camera.lookAt(scene.position);

      renderer.render(scene, camera);
    }
    animate();

    function handleVisibilityChange() {
      isVisible = document.visibilityState === 'visible';
      if (isVisible) animate();
    }
    document.addEventListener('visibilitychange', handleVisibilityChange);

    function handleResize() {
      camera.aspect = window.innerWidth / window.innerHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(window.innerWidth, window.innerHeight);
    }
    window.addEventListener('resize', handleResize);

    return () => {
      cancelAnimationFrame(animationFrameId);
      window.removeEventListener('resize', handleResize);
      window.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('visibilitychange', handleVisibilityChange);

      particleGeometry.dispose();
      particleMaterial.dispose();
      shapeGeometries.forEach((g) => g.dispose());
      shapes.forEach((mesh) => (mesh.material as THREE.Material).dispose());

      renderer.dispose();
      if (container.contains(renderer.domElement)) {
        container.removeChild(renderer.domElement);
      }
    };
  }, []);

  return (
    <div
      ref={containerRef}
      aria-hidden="true"
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: -1,
        background: 'linear-gradient(135deg, #0b1220 0%, #0f1b33 50%, #0b1220 100%)',
      }}
    />
  );
}
