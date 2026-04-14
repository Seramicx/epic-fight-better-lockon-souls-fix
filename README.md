# Better Lock-On Movement Fix

A companion mod for [Better Lockon](https://github.com/ShelMarow/Better-Lockon) and [Epic Fight](https://github.com/Antikythera-Studios/epicfight) on Minecraft Forge 1.20.1.

![Mod Showcase](https://raw.githubusercontent.com/Seramicx/Epic-Fight-Better-Lockon-Souls-Fix/assets/lockon_showcase_small.gif)

Better Lockon's movement has quite a few issues out of the box. In 1st person, trying to walk backwards or strafe just forces you straight into the enemy. In 3rd person, dodge rolls only go towards whoever you're locked onto, so you can never actually roll away. This mod fixes all of that and makes lock-on movement feel like a proper souls-like game.

## What it does

- **Full 360 movement and dodging** in any direction while locked on, in both 1st and 3rd person
- **Fixes the 1st person magnetism bug** where pressing back/sideways drags you into the enemy
- **Smooth turning** instead of the rigid 8-direction snapping
- **Stops camera drag** from Epic Fight constantly pulling your rotation toward the target
- **Controller support** via [Controllable](https://github.com/MrCrayfish/Controllable) for full analog stick movement (still needs testing)
- **Extended lock-on range** (configurable) so you can actually lock onto flying bosses

## Config

The mod creates a `lockonmovementfix-client.toml` in your config folder. Here's what you can change:

| Option | Default | What it does |
|--------|---------|-------------|
| `turnSpeed` | 0.45 | How fast you turn while moving. Lower = smoother, wider arcs |
| `idleTurnSpeed` | 0.70 | How fast you turn to face the target when standing still |
| `autoFaceTarget` | true | Auto-face the target when idle and during guard/parry. Set to false for full manual control |
| `lockOnRange` | 64 | Max lock-on distance in blocks. Vanilla Epic Fight caps out around 16-20 |

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
