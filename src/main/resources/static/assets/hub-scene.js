/* hub-scene.js — the routing constellation behind the console.
   A core (the AI) with tenant stations wired to it, and packets travelling the
   wires: inbound tickets into the core, dispatched answers back out.
   Colours are read from the active [data-theme] tokens, so the scene follows
   whichever theme the agent picks. WebGL or no WebGL, the app still works. */
import * as THREE from "three";

const VARIANTS = {
  full: { nodes: 12, radius: 3.1, spread: 0.5, packets: 30, stars: 260, spin: 0.055, parallax: true, dpr: 1.75 },
  mini: { nodes: 7, radius: 2.2, spread: 0.35, packets: 12, stars: 0, spin: 0.14, parallax: false, dpr: 1.5 },
};

const prefersReducedMotion = () =>
  window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;

/* Stations ride a wide ring, not a sphere: the login card sits over the core, so
   the wires have to read around its edges. Z wobble keeps the ring in depth. */
function ringPositions(count, radius, spread) {
  const pts = [];
  for (let i = 0; i < count; i++) {
    const a = (i / count) * Math.PI * 2;
    const wobble = 1 + ((i % 3) - 1) * spread * 0.26;
    pts.push(new THREE.Vector3(
      Math.cos(a) * radius * 1.45 * wobble,
      Math.sin(a) * radius * 0.95 * wobble,
      Math.sin(a * 2 + 0.6) * radius * 0.3
    ));
  }
  return pts;
}

function token(name, fallback) {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
}

export function createHubScene(canvas, variant = "full") {
  const cfg = VARIANTS[variant] || VARIANTS.full;
  if (!canvas || !canvas.getContext) return null;

  let renderer;
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, powerPreference: "low-power" });
    renderer.setClearColor(0x000000, 0);
  } catch {
    return null;                       // no WebGL — CSS backdrop takes over
  }

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 80);
  camera.position.set(0, 0, variant === "mini" ? 7.2 : 11.6);

  const root = new THREE.Group();
  scene.add(root);
  /* net = everything that orbits; the core stays un-scaled and round. */
  const net = new THREE.Group();
  net.rotation.x = 0.42;
  root.add(net);

  const stations = ringPositions(cfg.nodes, cfg.radius, cfg.spread);

  /* ---- palette (re-resolved whenever the theme changes) ---- */
  const palette = {};
  function resolvePalette() {
    palette.core = token("--scene-core", "#8B7CF6");
    palette.node = token("--scene-node", "#34D8C4");
    palette.link = token("--scene-link", "#3E4A61");
    palette.packet = token("--scene-packet", "#C9C1FF");
    palette.glow = token("--scene-glow", "1") === "1";
  }
  resolvePalette();

  const coreShell = new THREE.LineSegments(
    new THREE.WireframeGeometry(new THREE.IcosahedronGeometry(0.78, 1)),
    new THREE.LineBasicMaterial({ transparent: true, opacity: 0.62 })
  );
  const coreHeart = new THREE.Mesh(
    new THREE.IcosahedronGeometry(0.34, 1),
    new THREE.MeshBasicMaterial({ transparent: true, opacity: 0.85, wireframe: true })
  );
  root.add(coreShell, coreHeart);

  /* Station markers: one wireframe solid each, plus a bright dot. */
  const stationGroup = new THREE.Group();
  for (const p of stations) {
    const marker = new THREE.Mesh(
      new THREE.OctahedronGeometry(0.19, 0),
      new THREE.MeshBasicMaterial({ wireframe: true, transparent: true, opacity: 0.75 })
    );
    marker.position.copy(p);
    stationGroup.add(marker);
  }
  net.add(stationGroup);

  /* The ring itself, drawn as a guide line behind the stations. */
  const orbitPts = [];
  for (let i = 0; i < 72; i++) {
    const a = (i / 72) * Math.PI * 2;
    orbitPts.push(new THREE.Vector3(
      Math.cos(a) * cfg.radius * 1.45, Math.sin(a) * cfg.radius * 0.95, 0
    ));
  }
  const orbit = new THREE.LineLoop(
    new THREE.BufferGeometry().setFromPoints(orbitPts),
    new THREE.LineBasicMaterial({ transparent: true, opacity: 0.2 })
  );
  net.add(orbit);

  /* Wires: every station to the core, and to its nearest neighbour. */
  const linkPos = [];
  for (let i = 0; i < stations.length; i++) {
    const a = stations[i];
    linkPos.push(a.x, a.y, a.z, 0, 0, 0);
    let best = -1, bestD = Infinity;
    for (let j = 0; j < stations.length; j++) {
      if (i === j) continue;
      const d = a.distanceToSquared(stations[j]);
      if (d < bestD) { bestD = d; best = j; }
    }
    if (best >= 0) {
      const b = stations[best];
      linkPos.push(a.x, a.y, a.z, b.x, b.y, b.z);
    }
  }
  const linkGeom = new THREE.BufferGeometry();
  linkGeom.setAttribute("position", new THREE.Float32BufferAttribute(linkPos, 3));
  const links = new THREE.LineSegments(linkGeom,
    new THREE.LineBasicMaterial({ transparent: true, opacity: 0.42 }));
  net.add(links);

  /* Packets: inbound to the core, outbound dispatches back to a station. */
  const edges = stations.map((p) => p.clone());
  const count = cfg.packets;
  const pPos = new Float32Array(count * 3);
  const flight = [];
  for (let i = 0; i < count; i++) {
    flight.push({
      edge: i % edges.length,
      t: Math.random(),
      inbound: Math.random() > 0.42,
      speed: 0.16 + Math.random() * 0.22,
    });
  }
  const packetGeom = new THREE.BufferGeometry();
  packetGeom.setAttribute("position", new THREE.BufferAttribute(pPos, 3));
  const packets = new THREE.Points(packetGeom, new THREE.PointsMaterial({
    size: variant === "mini" ? 0.2 : 0.15, sizeAttenuation: true, transparent: true, opacity: 0.95,
  }));
  net.add(packets);

  /* Ambient queue dust, full variant only. */
  let stars = null;
  if (cfg.stars) {
    const sArr = new Float32Array(cfg.stars * 3);
    const v = new THREE.Vector3();
    for (let i = 0; i < cfg.stars; i++) {
      v.set(Math.random() * 2 - 1, Math.random() * 2 - 1, Math.random() * 2 - 1)
        .normalize().multiplyScalar(6 + Math.random() * 9);
      sArr[i * 3] = v.x; sArr[i * 3 + 1] = v.y; sArr[i * 3 + 2] = v.z;
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute("position", new THREE.BufferAttribute(sArr, 3));
    stars = new THREE.Points(g, new THREE.PointsMaterial({
      size: 0.035, sizeAttenuation: true, transparent: true, opacity: 0.5,
    }));
    scene.add(stars);
  }

  function applyPalette() {
    resolvePalette();
    const blend = palette.glow ? THREE.AdditiveBlending : THREE.NormalBlending;
    coreShell.material.color.set(palette.core);
    coreHeart.material.color.set(palette.packet);
    links.material.color.set(palette.link);
    orbit.material.color.set(palette.link);
    packets.material.color.set(palette.packet);
    packets.material.blending = blend;
    packets.material.needsUpdate = true;
    stationGroup.children.forEach((m, i) => {
      m.material.color.set(i % 3 === 0 ? palette.core : palette.node);
    });
    if (stars) {
      stars.material.color.set(palette.link);
      stars.material.blending = blend;
      stars.material.needsUpdate = true;
    }
  }
  applyPalette();

  /* ---- sizing ---- */
  let w = 0, h = 0;
  function resize() {
    const rect = canvas.getBoundingClientRect();
    w = Math.max(1, Math.round(rect.width));
    h = Math.max(1, Math.round(rect.height));
    camera.aspect = w / h;
    camera.fov = w / h < 0.9 ? 58 : 42;      // taller frames need a wider lens
    camera.updateProjectionMatrix();
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, cfg.dpr));
    renderer.setSize(w, h, false);
    fitNet();
    render();
  }

  /* Scale the orbit so the ring stays inside whatever frame it is given —
     a wide desktop shows the whole circuit, a phone shows the near arc. */
  function fitNet() {
    const halfH = Math.tan((camera.fov * Math.PI / 180) / 2) * camera.position.z;
    const halfW = halfH * camera.aspect;
    const fit = Math.min((halfW * 0.9) / (cfg.radius * 1.45), (halfH * 0.85) / (cfg.radius * 0.95));
    net.scale.setScalar(Math.min(1.35, Math.max(0.4, fit)));
  }

  /* ---- pointer parallax ---- */
  const pointer = { x: 0, y: 0 };
  const eased = { x: 0, y: 0 };
  function onPointer(e) {
    if (!cfg.parallax) return;
    pointer.x = (e.clientX / window.innerWidth) * 2 - 1;
    pointer.y = (e.clientY / window.innerHeight) * 2 - 1;
  }

  function stepPackets(dt) {
    for (let i = 0; i < count; i++) {
      const f = flight[i];
      f.t += dt * f.speed;
      if (f.t >= 1) {
        f.t = 0;
        f.inbound = Math.random() > 0.42;
        f.edge = Math.floor(Math.random() * edges.length);
        f.speed = 0.16 + Math.random() * 0.22;
      }
      const p = edges[f.edge];
      const k = f.inbound ? f.t : 1 - f.t;   // core sits at the origin
      pPos[i * 3] = p.x * (1 - k);
      pPos[i * 3 + 1] = p.y * (1 - k);
      pPos[i * 3 + 2] = p.z * (1 - k);
    }
    packetGeom.attributes.position.needsUpdate = true;
  }

  function render() {
    renderer.render(scene, camera);
  }

  /* ---- loop: paused whenever nothing is visible ---- */
  let enabled = true;
  let running = false;
  let last = 0;
  const reduced = prefersReducedMotion();

  function frame(now) {
    if (!running) return;
    const dt = Math.min(0.05, (now - last) / 1000 || 0);
    last = now;
    /* Stations travel along the ring in its own plane; the whole net is never
       tumbled edge-on, which is what made the wires vanish behind the card. */
    net.rotation.z += dt * cfg.spin;
    root.rotation.y = Math.sin(now / 5200) * 0.09;   // gentle sway, keeps the tilt readable
    coreShell.rotation.y -= dt * 0.06;
    coreHeart.rotation.y -= dt * 0.5;
    coreHeart.rotation.z += dt * 0.28;
    stepPackets(dt);
    if (stars) stars.rotation.y -= dt * 0.01;
    if (cfg.parallax) {
      eased.x += (pointer.x - eased.x) * 0.045;
      eased.y += (pointer.y - eased.y) * 0.045;
      camera.position.x = eased.x * 1.15;
      camera.position.y = -eased.y * 0.8;
      camera.lookAt(0, 0, 0);
    }
    render();
    requestAnimationFrame(frame);
  }

  function start() {
    if (running || reduced || !enabled) return;
    running = true;
    last = performance.now();
    requestAnimationFrame(frame);
  }
  function stop() { running = false; }

  function setEnabled(next) {
    enabled = next;
    if (next) start(); else stop();
  }

  /* A visible dispatch: a burst of packets leaves the core toward stations. */
  function emit(n = 5) {
    for (let i = 0; i < Math.min(n, count); i++) {
      const f = flight[(Math.random() * count) | 0];
      f.inbound = false;
      f.t = 0;
      f.edge = Math.floor(Math.random() * edges.length);
      f.speed = 0.5 + Math.random() * 0.35;
    }
    if (reduced || !running) { stepPackets(0.35); render(); }
  }

  const ro = new ResizeObserver(resize);
  ro.observe(canvas);
  resize();

  const onVisibility = () => (document.hidden ? stop() : start());
  document.addEventListener("visibilitychange", onVisibility);
  if (cfg.parallax) window.addEventListener("pointermove", onPointer, { passive: true });
  if (reduced) { stepPackets(0.25); render(); } else start();

  return {
    variant,
    emit,
    setEnabled,
    /* Called after a theme swap: rebuild colours from the new tokens. */
    setTheme() { applyPalette(); resize(); },
    destroy() {
      stop();
      ro.disconnect();
      document.removeEventListener("visibilitychange", onVisibility);
      window.removeEventListener("pointermove", onPointer);
      scene.traverse((o) => o.geometry && o.geometry.dispose());
      renderer.dispose();
    },
  };
}
