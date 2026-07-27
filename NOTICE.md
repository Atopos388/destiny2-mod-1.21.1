# Third-Party Code Notice

This project is distributed under the GNU General Public License v3.0 only.

The weapon ADS implementation contains code adapted from:

- Timeless and Classics Zero (TaCZ): https://github.com/MCModderAnchor/TACZ
- TaCZ gun-pack positioning reference revision:
  `b43eb84c38e9768d8e73c8b14f0b845669704b38` (the official `1.20.1` branch)
- TaCZ Refabricated: https://github.com/Sh1roCu/TACZ-Refabricated
- TaCZ Refabricated reference revision:
  `98ef5f4465bcbf185f6c570a178695d8929a2eac` (the `1.21.1` branch when integrated)
- TaCZ Refabricated third-person reference revision:
  `58a1c5107ee82e02a055c6cc7b82be1537db6b62` (the `1.21.1` branch when extended)

Upstream TaCZ and TaCZ Refabricated code is licensed under GPL-3.0. Adapted files
retain SPDX and source notices. The original authors retain copyright in their
contributions.

Adapted areas include ADS timing, FOV and sensitivity math, second-order
dynamics, the default entity eye-height headshot rule, recoil/crosshair
behavior, and weapon action-state structure.
The generic GeckoLib gun-pack adapter directly follows TaCZ's Bedrock pivot
conversion, positioning-bone path traversal, inverse view matrix order, and
the `idle_view` / `iron_view` / `thirdperson_hand` / `fixed` / `ground`
responsibility split.
The third-person integration also adapts the fallback responsibilities and
default arm pose from `InnerThirdPersonManager` and `ThirdPersonManager`.

No TaCZ gun-pack models, textures, sounds, or animations are incorporated. Those
assets have separate licensing terms. The Forgotten Name assets remain
project-provided assets.

## Source Han Sans

The Director UI bundles a static SemiBold TrueType instance generated from
Source Han Sans CN variable font version 2.005 from Adobe's project:

- https://github.com/adobe-fonts/source-han-sans
- Copyright 2014-2025 Adobe, with Reserved Font Name "Source"
- Licensed under the SIL Open Font License, Version 1.1

The complete font license is included at
`third_party/source-han-sans/LICENSE.txt`.
