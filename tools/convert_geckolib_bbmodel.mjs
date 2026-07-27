import fs from "node:fs";
import path from "node:path";

const [inputPath, outputPath, identifier = "geometry.converted"] = process.argv.slice(2);
if (!inputPath || !outputPath) {
  throw new Error("Usage: node tools/convert_geckolib_bbmodel.mjs <input.bbmodel> <output.geo.json> [identifier]");
}

const source = JSON.parse(fs.readFileSync(inputPath, "utf8"));
const elements = new Map(source.elements.map(element => [element.uuid, element]));
const groups = new Map((source.groups ?? []).map(group => [group.uuid, group]));
const bones = [];

function mirrorVector(vector = [0, 0, 0], rotation = false) {
  return rotation
    ? [-vector[0], -vector[1], vector[2]]
    : [-vector[0], vector[1], vector[2]];
}

function convertFaces(faces = {}) {
  const converted = {};
  for (const [name, face] of Object.entries(faces)) {
    if (face.texture == null || !face.uv) continue;
    const [u0, v0, u1, v1] = face.uv;
    converted[name] = {
      uv: [u0, v0],
      uv_size: [u1 - u0, v1 - v0]
    };
  }
  return converted;
}

function convertCube(element) {
  const size = element.to.map((value, index) => value - element.from[index]);
  const cube = {
    name: element.name,
    origin: [-element.to[0], element.from[1], element.from[2]],
    size,
    pivot: mirrorVector(element.origin),
    rotation: mirrorVector(element.rotation, true),
    uv: convertFaces(element.faces)
  };
  if (element.inflate) cube.inflate = element.inflate;
  if (element.mirror_uv) cube.mirror = true;
  return cube;
}

function walk(node, parentName = null) {
  if (typeof node === "string") return;
  const group = groups.get(node.uuid);
  if (!group) return;
  const bone = {
    name: group.name,
    pivot: mirrorVector(group.origin),
    rotation: mirrorVector(group.rotation, true)
  };
  if (parentName) bone.parent = parentName;

  const cubes = [];
  for (const child of node.children ?? []) {
    if (typeof child === "string") {
      const element = elements.get(child);
      if (element?.type === "cube") cubes.push(convertCube(element));
    }
  }
  if (cubes.length) bone.cubes = cubes;
  bones.push(bone);

  for (const child of node.children ?? []) {
    if (typeof child !== "string") walk(child, group.name);
  }
}

for (const node of source.outliner ?? []) walk(node);

const output = {
  format_version: "1.12.0",
  "minecraft:geometry": [{
    description: {
      identifier,
      texture_width: source.resolution?.width ?? 256,
      texture_height: source.resolution?.height ?? 256,
      visible_bounds_width: 12,
      visible_bounds_height: 8,
      visible_bounds_offset: [0, 3, 0]
    },
    bones
  }]
};

fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, `${JSON.stringify(output, null, 2)}\n`);
