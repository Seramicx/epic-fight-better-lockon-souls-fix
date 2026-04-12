# Better Lock-On Movement Fix

This is a companion mod for the [Better Lockon](https://github.com/ShelMarow/Better-Lockon) and [Epic Fight](https://github.com/Antikythera-Studios/epicfight) ecosystem in Minecraft Forge 1.20.1.

![Mod Showcase](https://raw.githubusercontent.com/Seramicx/Epic-Fight-Better-Lockon-Souls-Fix/assets/lockon_showcase_small.gif)

The original Better Lockon mod had several game-breaking movement bugs depending on what perspective you were playing in. If you were in 1st person, pressing S to walk backwards or A/D to strafe would actively force your character to walk forward straight into the enemy. In 3rd person, while basic movement worked, combat actions were completely broken. If you tried to use Epic Fight's dodge roll to escape, you could only roll directly towards the enemy you were locked onto. Rolling away or dodging sideways was impossible.

This fix overhauls the movement logic to solve all these issues and brings the lock-on movement closer to traditional 3rd person souls-like action games.

## Features and Fixes

* **True 360 Degree Movement & Dodging:** You can finally run and roll away from enemies. By syncing your character's actual rotation to your movement input, Epic Fight now registers your directional keys correctly. Pressing S will let you sprint or dodge roll away from the target reliably in both 1st and 3rd person.
* **Fixes 1st Person Magnetism:** Completely removes the bug where pressing back or sideways in 1st person forces you to walk towards the target. You now have full control over your character's spacing.
* **Smooth Turning:** Removed the rigid 8-directional angle snapping. The player now smoothly interpolates between angles when you change directions. This gives your character a much more natural curve when moving around.
* **Kills Camera Drag:** Epic Fight has a background tick process that constantly tries to pull your character's rotation towards the target. This fix bypasses that behavior entirely so your movement direction is no longer actively fighting against the camera code.
* **Performance Optimizations:** The movement handlers have been cleaned up and streamlined. Repeated API calls to Epic Fight are now cached completely to avoid wasting performance on fetching the camera instance every single tick.

## Configuration

The mod generates a `lockonmovementfix-client.toml` file in your main config folder. You can tune these values to fit exactly how you want it to feel:

* `turnSpeed` (default: 0.45): Controls how fast your character rotates while running. Lower values give you a smoother, wider turn arc.
* `idleTurnSpeed` (default: 0.70): Controls how fast your character turns to face the enemy when you stop moving. It is set a bit higher by default so your attacks actually land where you want them to.

## Requirements

* Minecraft Forge 1.20.1
* Epic Fight
* Better Lockon

## Installation

You can download the latest `.jar` file directly from the [Releases](../../releases) tab on this page and drop it into your `mods` folder.

<details>
<summary>Building from source</summary>

If you want to compile the project yourself instead:
1. Clone the repository
2. Run `gradlew build` in the root folder
3. The compiled jar will show up in the `build/libs` directory
</details>

## License

**Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0)**

You are free to:
* **Share** - copy and redistribute the material in any medium or format
* **Adapt** - remix, transform, and build upon the material

Under the following terms:
* **Attribution** - You must give appropriate credit.
* **NonCommercial** - You may not use the material for commercial purposes (no monetized downloads or ad-focus links).
* **ShareAlike** - If you remix, transform, or build upon the material, you must distribute your contributions under the same license as the original.
