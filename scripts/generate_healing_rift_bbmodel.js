const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const root = path.resolve(__dirname, "..");
const modelDir = path.join(root, "src/main/resources/assets/destiny2-mod/blockbench");
const outPath = path.join(modelDir, "warlock_healing_rift_elaborate.bbmodel");

function id() {
  return crypto.randomUUID();
}

function cube(name, from, to, origin = [0, 0, 0], rotation = [0, 0, 0], color = 5) {
  const uuid = id();
  return {
    uuid,
    element: {
      name,
      box_uv: true,
      render_order: "default",
      locked: false,
      allow_mirror_modeling: true,
      from,
      to,
      autouv: 0,
      color,
      origin,
      rotation,
      faces: {
        north: { uv: [0, 0, 8, 8], texture: 0 },
        east: { uv: [8, 0, 16, 8], texture: 0 },
        south: { uv: [16, 0, 24, 8], texture: 0 },
        west: { uv: [24, 0, 32, 8], texture: 0 },
        up: { uv: [32, 0, 40, 8], texture: 0 },
        down: { uv: [40, 0, 48, 8], texture: 0 }
      },
      type: "cube",
      uuid
    }
  };
}

const elements = [];
const groups = [];
const outliner = [];

function group(name, origin = [0, 0, 0], rotation = [0, 0, 0], color = 0) {
  const uuid = id();
  const children = [];
  groups.push({
    uuid,
    export: true,
    locked: false,
    origin,
    rotation,
    color,
    name,
    children,
    reset: false,
    shade: true,
    mirror_uv: false,
    selected: false,
    visibility: true,
    autouv: 0,
    isOpen: true
  });
  outliner.push({ uuid, isOpen: true, children });
  return { uuid, name, children };
}

function add(parent, created) {
  elements.push(created.element);
  parent.children.push(created.uuid);
}

const base = group("base_magic_circle", [0, 0, 0], [0, 0, 0], 1);
const sigils = group("rotating_inner_sigils", [0, 0.2, 0], [0, 0, 0], 2);
const upper = group("floating_upper_ring", [0, 24, 0], [18, 0, 0], 3);
const core = group("sun_core_crystal", [0, 15, 0], [0, 45, 0], 4);
const spires = group("vertical_light_spires", [0, 0, 0], [0, 0, 0], 5);
const shards = group("orbiting_rune_shards", [0, 18, 0], [0, 0, 0], 6);

for (let i = 0; i < 32; i++) {
  add(base, cube(`outer_ring_segment_${i}`, [-1.1, 0.2, -44], [1.1, 1.2, -35], [0, 0, 0], [0, i * 11.25, 0], 1));
}

for (let i = 0; i < 16; i++) {
  add(base, cube(`golden_ray_${i}`, [-0.6, 0.45, -31], [0.6, 1.0, -14], [0, 0, 0], [0, i * 22.5 + 11.25, 0], 2));
}

for (let i = 0; i < 12; i++) {
  add(sigils, cube(`inner_rune_bar_${i}`, [-0.7, 2.2, -24], [0.7, 3.0, -17], [0, 0, 0], [0, i * 30, 0], 3));
  add(sigils, cube(`inner_rune_tick_${i}`, [-2.2, 2.4, -22], [2.2, 3.2, -20.6], [0, 0, 0], [0, i * 30 + 12, 0], 4));
}

for (let i = 0; i < 24; i++) {
  add(upper, cube(`upper_halo_segment_${i}`, [-0.75, 23.1, -30], [0.75, 24.8, -24], [0, 24, 0], [18, i * 15, 0], 5));
}

add(core, cube("crystal_body_a", [-3, 9, -3], [3, 21, 3], [0, 15, 0], [0, 45, 0], 6));
add(core, cube("crystal_body_b", [-2.4, 7, -2.4], [2.4, 23, 2.4], [0, 15, 0], [0, 0, 45], 6));
add(core, cube("crystal_cap_top", [-1.2, 21, -1.2], [1.2, 27, 1.2], [0, 15, 0], [0, 45, 0], 6));
add(core, cube("crystal_cap_bottom", [-1.2, 3, -1.2], [1.2, 9, 1.2], [0, 15, 0], [0, 45, 0], 6));

for (let i = 0; i < 8; i++) {
  add(spires, cube(`soft_light_spire_${i}`, [-0.45, 1, -36], [0.45, 22, -35], [0, 0, 0], [0, i * 45, 0], 5));
}

for (let i = 0; i < 16; i++) {
  const angle = i * Math.PI * 2 / 16;
  const x = Math.sin(angle) * 34;
  const z = -Math.cos(angle) * 34;
  const y = 11 + (i % 4) * 3;
  add(shards, cube(`floating_rune_shard_${i}`, [x - 1.2, y - 2, z - 0.45], [x + 1.2, y + 2, z + 0.45], [0, 18, 0], [12, i * 22.5, i % 2 ? 18 : -18], 7));
}

function key(channel, data, time, interpolation = "linear") {
  return { channel, data_points: [data], time, interpolation, uuid: id() };
}

const animations = [{
  uuid: id(),
  name: "idle",
  loop: "loop",
  override: false,
  length: 4,
  snapping: 20,
  selected: false,
  animators: {
    [base.uuid]: {
      name: base.name,
      type: "bone",
      keyframes: [key("rotation", { x: 0, y: 0, z: 0 }, 0), key("rotation", { x: 0, y: 360, z: 0 }, 4)]
    },
    [sigils.uuid]: {
      name: sigils.name,
      type: "bone",
      keyframes: [key("rotation", { x: 0, y: 0, z: 0 }, 0), key("rotation", { x: 0, y: -360, z: 0 }, 4)]
    },
    [upper.uuid]: {
      name: upper.name,
      type: "bone",
      keyframes: [key("rotation", { x: 18, y: 0, z: 0 }, 0), key("rotation", { x: 18, y: -360, z: 0 }, 4)]
    },
    [core.uuid]: {
      name: core.name,
      type: "bone",
      keyframes: [
        key("rotation", { x: 0, y: 45, z: 0 }, 0),
        key("rotation", { x: 0, y: 405, z: 0 }, 4),
        key("position", { x: 0, y: 0, z: 0 }, 0, "catmullrom"),
        key("position", { x: 0, y: 2, z: 0 }, 1, "catmullrom"),
        key("position", { x: 0, y: 0, z: 0 }, 2, "catmullrom"),
        key("position", { x: 0, y: -1, z: 0 }, 3, "catmullrom"),
        key("position", { x: 0, y: 0, z: 0 }, 4, "catmullrom")
      ]
    },
    [shards.uuid]: {
      name: shards.name,
      type: "bone",
      keyframes: [key("rotation", { x: 0, y: 0, z: 0 }, 0), key("rotation", { x: 0, y: 360, z: 0 }, 4)]
    }
  }
}];

const bbmodel = {
  meta: { format_version: "5.0", model_format: "geckolib_model", box_uv: true },
  name: "warlock_healing_rift_elaborate",
  model_identifier: "geometry.healing_rift",
  front_gui_light: false,
  visible_box: [6, 4, 6],
  variable_placeholders: " ",
  variable_placeholder_buttons: [],
  timeline_setups: [],
  unhandled_root_fields: {},
  geckolib_modid: "destiny2-mod",
  geckolib_filepath_cache: {
    model: path.join(root, "src/main/resources/assets/destiny2-mod/geo/healing_rift.geo.json"),
    animation: path.join(root, "src/main/resources/assets/destiny2-mod/animations/healing_rift.animation.json")
  },
  resolution: { width: 64, height: 64 },
  elements,
  groups,
  outliner,
  textures: [{
    name: "healing_rift.png",
    relative_path: "../textures/entity/healing_rift.png",
    folder: "block",
    namespace: "",
    id: "0",
    group: "",
    width: 64,
    height: 64,
    uv_width: 64,
    uv_height: 64,
    particle: false,
    use_as_default: true,
    layers_enabled: false,
    sync_to_project: "",
    render_mode: "additive",
    render_sides: "double",
    pbr_channel: "color",
    frame_time: 1,
    frame_order_type: "loop",
    frame_order: "",
    frame_interpolate: false,
    visible: true,
    internal: false,
    saved: true,
    uuid: id(),
    source: "../textures/entity/healing_rift.png"
  }],
  animations
};

fs.mkdirSync(modelDir, { recursive: true });
fs.writeFileSync(outPath, JSON.stringify(bbmodel, null, 2), "utf8");
console.log(outPath);
