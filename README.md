# Better Lock-On Movement Fix

A companion mod for [Better Lockon](https://github.com/ShelMarow/Better-Lockon) and [Epic Fight](https://github.com/Antikythera-Studios/epicfight) on Minecraft Forge 1.20.1.

![Mod Showcase](https://raw.githubusercontent.com/Seramicx/Epic-Fight-Better-Lockon-Souls-Fix/assets/lockon_showcase_small.gif)

Better Lockon's movement has some pretty rough issues out of the box. In 1st person, trying to walk backwards or strafe just forces you straight into the enemy. In 3rd person, dodge rolls only go towards whoever you're locked onto, so you can never actually roll away. This mod fixes all of that and makes lock-on movement feel like a proper souls-like game.

## What it does

- **Full 360 movement and dodging** in any direction while locked on, in both 1st and 3rd person
- **Fixes the 1st person magnetism bug** where pressing back/sideways drags you into the enemy
- **Smooth turning** instead of the rigid 8-direction snapping
- **Stops camera drag** from Epic Fight constantly pulling your rotation toward the target
- **Guard and parry compatibility** with Better Lock On's auto-facing during blocking
- **Controller support** via [Controllable](https://github.com/MrCrayfish/Controllable) — full analog stick movement and 360 dodge
- **Extended lock-on range** (configurable) so you can actually lock onto flying bosses
- **Auto lock-on** — Elden Ring-style automatic target switching when your current target dies, with directional mouse flick to manually switch between targets
- **Over-the-shoulder camera** — lateral + vertical camera offset in 3rd person, with shoulder swap keybind and wall collision so the camera doesn't clip through blocks
- **Crosshair correction** — when the camera is offset, block/entity interaction aligns with where the crosshair actually points on screen
- **Adaptive player hiding** — the player model disappears when the camera is pushed too close (e.g., backed against a wall) to prevent clipping into the model

You also get a **third-person shoulder-style camera** (slide it off to the side, swap which shoulder, nudge it up/down), **crosshair that lines up with what you're actually aiming at**, and that little trick where **you don't eat the whole screen** when you back into a wall — all optional, all in the config.

## Keybinds

All of these live under the **Lock-On Movement Fix** category in Controls.

- **Toggle Auto Lock-On** — unbound by default (bind it if you want the feature).
- **Swap Shoulder** — defaults to **O** (feel free to rebind).

## Auto Lock-On

Bind the **Toggle Auto Lock-On** key in your controls menu (unbound by default). When enabled:

1. Lock onto a target normally with your existing lock-on key
2. When that target dies, you hop to someone else that still feels fair — usually whoever's in front of you, without totally ignoring a dude glued to your back
3. **Mouse flick** left or right to manually switch targets in that direction
4. If no valid targets are in range, lock-on releases — you'll need to manually lock on again to resume auto-switching

The target selection uses a forward-facing camera cone. Enemies in front of you are strongly preferred, but very close enemies slightly behind you can still be selected if their proximity outweighs the angular penalty.

## Config

The mod creates a `lockonmovementfix-client.toml` in your config folder. Here's what you can change:

| Option | Default | What it does |
|--------|---------|-------------|
| `turnSpeed` | 0.45 | How fast you turn while moving. Lower = smoother, wider arcs |
| `idleTurnSpeed` | 0.70 | How fast you turn to face the target when standing still |
| `autoFaceTarget` | true | Auto-face the target when idle and during guard/parry. Set to false for full manual control |
| `lockOnRange` | 64 | Max lock-on distance in blocks. Vanilla Epic Fight caps out around 16-20 |
| `filterPlayersFromAutoLockOn` | true | Exclude other players from auto target switching (good for co-op) |
| `flickSensitivity` | 15.0 | Degrees of mouse movement to trigger a directional target switch (5-45) |
| `cameraOffsetX` | -0.75 | Horizontal camera offset in blocks. Negative = left shoulder, positive = right. 0 = centered |
| `cameraOffsetY` | 0.15 | Vertical camera offset in blocks |
| `cameraOffsetSmoothing` | 0.5 | How fast the camera transitions when swapping shoulders (0.05 to 1.0) |
| `hidePlayerWhenClose` | true | Hide the player model when the camera is pushed very close |
| `hidePlayerDistance` | 0.8 | Distance threshold for hiding the player model |

## Requirements

- Minecraft Forge 1.20.1
- Epic Fight
- Better Lockon

## Install

Grab the latest jar from the [Releases](../../releases) page and drop it in your mods folder.

<details>
<summary>Building from source</summary>

1. Clone the repo
2. Run `gradlew build`
3. Jar ends up in `build/libs`
</details>

## License

GPL-3.0. See [LICENSE](LICENSE) for details.
