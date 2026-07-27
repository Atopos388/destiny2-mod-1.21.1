import fs from "node:fs";
import path from "node:path";

const [input, output] = process.argv.slice(2);
if (!input || !output) {
  throw new Error("Usage: node scripts/integrate-forgotten-name-animation.mjs <source.animation.json> <runtime.animation.json>");
}

const sourceFile = path.resolve(input);
const outputFile = path.resolve(output);
const root = JSON.parse(fs.readFileSync(sourceFile, "utf8"));
const animations = root.animations ?? {};

for (const required of ["static_idle", "shoot", "reload_tactical"]) {
  if (!animations[required]) throw new Error(`Missing required animation: ${required}`);
}

function replaceVector(animationName, boneName, channelName, time, vector) {
  const keyframe = animations[animationName]?.bones?.[boneName]?.[channelName]?.[time];
  if (!keyframe) {
    throw new Error(`Missing keyframe: ${animationName}.${boneName}.${channelName}@${time}`);
  }

  if (Array.isArray(keyframe)) {
    animations[animationName].bones[boneName][channelName][time] = [...vector];
    return;
  }

  if (Array.isArray(keyframe.vector)) {
    keyframe.vector = [...vector];
    return;
  }

  throw new Error(`Unsupported keyframe shape: ${animationName}.${boneName}.${channelName}@${time}`);
}

function keyframeVectors(keyframe, animationName, boneName, channelName, time) {
  if (Array.isArray(keyframe)) return [keyframe];
  if (Array.isArray(keyframe?.vector)) return [keyframe.vector];

  const vectors = [];
  for (const edge of ["pre", "post"]) {
    if (Array.isArray(keyframe?.[edge])) vectors.push(keyframe[edge]);
    else if (Array.isArray(keyframe?.[edge]?.vector)) vectors.push(keyframe[edge].vector);
  }
  if (vectors.length > 0) return vectors;

  throw new Error(`Unsupported keyframe shape: ${animationName}.${boneName}.${channelName}@${time}`);
}

function unwrapRotationAxis(animationName, boneName, axis) {
  const channelName = "rotation";
  const channel = animations[animationName]?.bones?.[boneName]?.[channelName];
  if (!channel) throw new Error(`Missing channel: ${animationName}.${boneName}.${channelName}`);

  const entries = Object.entries(channel)
    .filter(([time]) => Number.isFinite(Number(time)))
    .sort(([a], [b]) => Number(a) - Number(b));
  let previous;
  let revolutionOffset = 0;

  for (const [time, keyframe] of entries) {
    const vectors = keyframeVectors(keyframe, animationName, boneName, channelName, time);
    // For a Bedrock pre/post discontinuity, post is the pose that should feed
    // the following segment. The exporter uses this shape for the 375 -> 15
    // equivalent-angle wrap that GeckoLib otherwise interprets incorrectly.
    let value = Number(vectors[vectors.length - 1][axis]) + revolutionOffset;

    if (previous !== undefined) {
      while (value - previous <= -180) {
        revolutionOffset += 360;
        value += 360;
      }
      while (value - previous > 180) {
        revolutionOffset -= 360;
        value -= 360;
      }
    }

    for (const vector of vectors) vector[axis] = Number(value.toFixed(4));
    previous = value;
  }
}

// Blockbench writes the end of each cylinder spin as 375 -> 15 degrees.
// GeckoLib interpolates raw Euler values and therefore renders that equivalent
// angle wrap as a reverse 360-degree spin over one or two frames. Keep the
// authored poses but unwrap the X channel so the numeric curve stays continuous.
unwrapRotationAxis("draw", "bone5", 0);
unwrapRotationAxis("inspect", "bone5", 0);

// The exported inspect settle crosses past the idle pose at 2.2589 s and then
// returns to zero 30.5 ms later. GeckoLib renders that reversal as a visible
// one-frame flash. Keep the authored timing and terminal pose, but place the
// intermediate gun/camera samples on the monotonic path toward idle.
replaceVector("inspect", "gun_and_righthand", "rotation", "2.2589", [-1.3333, -2.6667, -1]);
replaceVector("inspect", "gun_and_righthand", "position", "2.2589", [-0.04, 0.2333, 0.2667]);
replaceVector("inspect", "camera", "rotation", "2.2589", [-0.0028, 0.0018, -0.002]);

// Gunshot audio is server-authoritative. Keeping the exported sound keyframe
// would create a second client-side authority for the same shot.
delete animations.shoot.sound_effects;

// These events are runtime integration metadata and are not authored motion.
animations.reload_tactical.sound_effects ??= {
  "0.0417": { effect: "huandan", bind_to_actor: false }
};
animations.reload_tactical.particle_effects ??= {
  "0.8333": { effect: "ancient_city_weapon_echo", locator: "locator2" }
};

const compatibilityAnimations = {
  static_bolt_caught: true,
  put_away: false,
  reload_empty: false,
  inspect_empty: false,
  bolt: false,
  run_start: false,
  run: true,
  run_hold: true,
  run_end: false,
  walk_aiming: true,
  walk_forward: true,
  walk_backward: true,
  walk_sideway: true
};

for (const [name, loop] of Object.entries(compatibilityAnimations)) {
  animations[name] ??= {
    ...(loop ? { loop: true } : {}),
    animation_length: 0.1,
    destiny2_placeholder: true,
    bones: {}
  };
}

fs.writeFileSync(outputFile, `${JSON.stringify(root, null, "\t")}\n`, "utf8");
process.stdout.write(`Integrated ${Object.keys(animations).length} animations from ${sourceFile}\n`);
