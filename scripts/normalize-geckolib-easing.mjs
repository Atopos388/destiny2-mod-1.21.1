import fs from "node:fs";
import path from "node:path";

const input = process.argv[2];
if (!input) {
  throw new Error("Usage: node scripts/normalize-geckolib-easing.mjs <animation.json>");
}

const file = path.resolve(input);
const root = JSON.parse(fs.readFileSync(file, "utf8"));
let converted = 0;

for (const animation of Object.values(root.animations ?? {})) {
  for (const bone of Object.values(animation.bones ?? {})) {
    for (const channelName of ["rotation", "position", "scale"]) {
      const channel = bone[channelName];
      if (!channel || Array.isArray(channel) || typeof channel !== "object") continue;

      for (const [timestamp, keyframe] of Object.entries(channel)) {
        if (!keyframe || typeof keyframe !== "object" || keyframe.lerp_mode !== "catmullrom") continue;

        // GeckoLib 4.6.6 discards lerp_mode when it unwraps Bedrock pre/post
        // keyframes. Flatten equal pre/post values into the supported vector +
        // easing representation. easeinoutsine is the stable smooth equivalent;
        // GeckoLib 4.6.6's catmullrom easing implementation is not usable here.
        const rawVector = keyframe.vector ?? keyframe.post?.vector ?? keyframe.post ??
          keyframe.pre?.vector ?? keyframe.pre;
        if (!Array.isArray(rawVector) || rawVector.length !== 3) {
          throw new Error(`Cannot normalize ${timestamp} in ${channelName}: missing three-axis vector`);
        }

        channel[timestamp] = {
          vector: rawVector,
          easing: "easeinoutsine"
        };
        converted++;
      }
    }
  }
}

fs.writeFileSync(file, `${JSON.stringify(root, null, "\t")}\n`, "utf8");
process.stdout.write(`Normalized ${converted} GeckoLib keyframes in ${file}\n`);
