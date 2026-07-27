const ENDPOINT = "http://localhost:3000/bb-mcp";

let nextId = 1;
let sessionId = null;

async function rpc(method, params = {}) {
  const headers = {
    "Accept": "application/json, text/event-stream",
    "Content-Type": "application/json"
  };
  if (sessionId) headers["mcp-session-id"] = sessionId;

  const response = await fetch(ENDPOINT, {
    method: "POST",
    headers,
    body: JSON.stringify({ jsonrpc: "2.0", id: nextId++, method, params })
  });

  const text = await response.text();
  if (response.headers.get("mcp-session-id")) {
    sessionId = response.headers.get("mcp-session-id");
  }
  const payload = text ? JSON.parse(text) : {};
  if (!response.ok || payload.error) {
    throw new Error(`${method} failed: ${text}`);
  }
  return payload.result;
}

async function notifyInitialized() {
  await fetch(ENDPOINT, {
    method: "POST",
    headers: {
      "Accept": "application/json, text/event-stream",
      "Content-Type": "application/json",
      "mcp-session-id": sessionId
    },
    body: JSON.stringify({ jsonrpc: "2.0", method: "notifications/initialized", params: {} })
  });
}

async function callTool(name, args = {}) {
  return rpc("tools/call", { name, arguments: args });
}

function cube(name, from, to, rotation = [0, 0, 0], origin = [0, 0, 0]) {
  return { name, from, to, rotation, origin };
}

async function main() {
  const init = await rpc("initialize", {
    protocolVersion: "2024-11-05",
    capabilities: {},
    clientInfo: { name: "codex-blockbench-builder", version: "1.0.0" }
  });
  await notifyInitialized();
  console.log(`Connected to ${init.serverInfo.name} ${init.serverInfo.version}`);

  await callTool("create_project", {
    name: "warlock_healing_rift_elaborate",
    format: "geckolib_model"
  });

  await callTool("create_texture", {
    name: "healing_rift_gold_energy",
    width: 64,
    height: 64,
    fill_color: "#FFD76ACC",
    layer_name: "gold_energy",
    render_mode: "additive",
    render_sides: "double"
  });

  const groups = [
    ["base_magic_circle", [0, 0, 0], [0, 0, 0]],
    ["rotating_inner_sigils", [0, 0.2, 0], [0, 0, 0]],
    ["floating_upper_ring", [0, 24, 0], [18, 0, 0]],
    ["sun_core_crystal", [0, 15, 0], [0, 45, 0]],
    ["vertical_light_spires", [0, 0, 0], [0, 0, 0]],
    ["orbiting_rune_shards", [0, 18, 0], [0, 0, 0]]
  ];
  for (const [name, origin, rotation] of groups) {
    await callTool("add_group", { name, origin, rotation, shade: false, autouv: "1" });
  }

  const baseCubes = [];
  for (let i = 0; i < 32; i++) {
    baseCubes.push(cube(`outer_ring_segment_${i}`, [-1.1, 0.2, -44], [1.1, 1.2, -35], [0, i * 11.25, 0]));
  }
  for (let i = 0; i < 16; i++) {
    baseCubes.push(cube(`golden_ray_${i}`, [-0.6, 0.45, -31], [0.6, 1.0, -14], [0, i * 22.5 + 11.25, 0]));
  }
  await callTool("place_cube", {
    group: "base_magic_circle",
    texture: "healing_rift_gold_energy",
    faces: true,
    elements: baseCubes
  });

  const sigilCubes = [];
  for (let i = 0; i < 12; i++) {
    sigilCubes.push(cube(`inner_rune_bar_${i}`, [-0.7, 2.2, -24], [0.7, 3.0, -17], [0, i * 30, 0]));
    sigilCubes.push(cube(`inner_rune_tick_${i}`, [-2.2, 2.4, -22], [2.2, 3.2, -20.6], [0, i * 30 + 12, 0]));
  }
  await callTool("place_cube", {
    group: "rotating_inner_sigils",
    texture: "healing_rift_gold_energy",
    faces: true,
    elements: sigilCubes
  });

  const upperCubes = [];
  for (let i = 0; i < 24; i++) {
    upperCubes.push(cube(`upper_halo_segment_${i}`, [-0.75, 23.1, -30], [0.75, 24.8, -24], [18, i * 15, 0], [0, 24, 0]));
  }
  await callTool("place_cube", {
    group: "floating_upper_ring",
    texture: "healing_rift_gold_energy",
    faces: true,
    elements: upperCubes
  });

  await callTool("place_cube", {
    group: "sun_core_crystal",
    texture: "healing_rift_gold_energy",
    faces: true,
    elements: [
      cube("crystal_body_a", [-3, 9, -3], [3, 21, 3], [0, 45, 0], [0, 15, 0]),
      cube("crystal_body_b", [-2.4, 7, -2.4], [2.4, 23, 2.4], [0, 0, 45], [0, 15, 0]),
      cube("crystal_cap_top", [-1.2, 21, -1.2], [1.2, 27, 1.2], [0, 45, 0], [0, 15, 0]),
      cube("crystal_cap_bottom", [-1.2, 3, -1.2], [1.2, 9, 1.2], [0, 45, 0], [0, 15, 0])
    ]
  });

  const spireCubes = [];
  for (let i = 0; i < 8; i++) {
    spireCubes.push(cube(`soft_light_spire_${i}`, [-0.45, 1, -36], [0.45, 22, -35], [0, i * 45, 0]));
  }
  await callTool("place_cube", {
    group: "vertical_light_spires",
    texture: "healing_rift_gold_energy",
    faces: true,
    elements: spireCubes
  });

  const shardCubes = [];
  for (let i = 0; i < 16; i++) {
    const angle = i * Math.PI * 2 / 16;
    const x = Math.sin(angle) * 34;
    const z = -Math.cos(angle) * 34;
    const y = 11 + (i % 4) * 3;
    shardCubes.push(cube(`floating_rune_shard_${i}`, [x - 1.2, y - 2, z - 0.45], [x + 1.2, y + 2, z + 0.45], [12, i * 22.5, i % 2 ? 18 : -18], [0, 18, 0]));
  }
  await callTool("place_cube", {
    group: "orbiting_rune_shards",
    texture: "healing_rift_gold_energy",
    faces: true,
    elements: shardCubes
  });

  await callTool("create_animation", {
    name: "animation.healing_rift.idle",
    loop: true,
    animation_length: 4,
    bones: {
      base_magic_circle: [
        { time: 0, rotation: [0, 0, 0] },
        { time: 4, rotation: [0, 360, 0] }
      ],
      rotating_inner_sigils: [
        { time: 0, rotation: [0, 0, 0] },
        { time: 4, rotation: [0, -360, 0] }
      ],
      floating_upper_ring: [
        { time: 0, rotation: [18, 0, 0], position: [0, 0, 0] },
        { time: 2, position: [0, 2, 0] },
        { time: 4, rotation: [18, -360, 0], position: [0, 0, 0] }
      ],
      sun_core_crystal: [
        { time: 0, rotation: [0, 45, 0], position: [0, 0, 0], scale: [1, 1, 1] },
        { time: 1, position: [0, 2, 0], scale: [1.08, 1.14, 1.08] },
        { time: 2, position: [0, 0, 0], scale: [1, 1, 1] },
        { time: 3, position: [0, -1, 0], scale: [0.95, 0.98, 0.95] },
        { time: 4, rotation: [0, 405, 0], position: [0, 0, 0], scale: [1, 1, 1] }
      ],
      orbiting_rune_shards: [
        { time: 0, rotation: [0, 0, 0] },
        { time: 4, rotation: [0, 360, 0] }
      ],
      vertical_light_spires: [
        { time: 0, scale: [1, 0.35, 1] },
        { time: 1, scale: [1, 1.2, 1] },
        { time: 2, scale: [1, 0.55, 1] },
        { time: 3, scale: [1, 1.35, 1] },
        { time: 4, scale: [1, 0.35, 1] }
      ]
    }
  });

  const info = await callTool("get_project_info", {});
  console.log(JSON.stringify(info, null, 2));
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
