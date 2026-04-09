# Better Lock-On Movement Fix

This is a companion mod for the [Better Lockon](https://github.com/ShelMarow/Better-Lockon) and [Epic Fight](https://github.com/Antikythera-Studios/epicfight) ecosystem in Minecraft Forge 1.20.1.

If you've played with the original Better Lockon mod, you might have noticed some clunky movement behavior. Pressing 'S' to walk backwards actually forces you to walk forwards towards the enemy. If you try to strafe around a target, your character awkwardly shuffles sideways at walking speed while facing them. The turning itself is also very rigid, locking you into stiff 8-directional geometry. 

This mod fixes all of that by overhauling the movement logic to match traditional 3rd person action games like Elden Ring.

## Features and Fixes

* **True 360 Degree Movement:** You can finally run away from enemies. When you press S, your character turns around and runs back at full speed while the camera stays locked on. The same applies for diagonals and strafing. Your body faces the exact direction you are moving, letting you run at full speed instead of walking or shuffling. When you stop moving to attack, your character smoothly snaps back to face the target.
* **Smooth Turning:** Removed the rigid 8-directional angle snapping. The player now smoothly interpolates between angles when you change directions. This gives your character a much more natural curve when moving around.
* **Kills Camera Drag:** Epic Fight has a background tick process that constantly tries to pull your character's rotation towards the target. This fix bypasses that behavior entirely, meaning your movement direction is no longer actively fighting against the camera code.
* **Extended Lock-On Distance:** By default, Epic Fight's lock-on range is way too short for flying bosses like the Wither and Ender Dragon. The moment they fly up, you lose your lock. This mod uses reflection to override Epic Fight's internal config and extends the range so you can stay locked on to airborne targets.
* **Performance Optimizations:** The movement handlers have been cleaned up and streamlined. Repeated API calls to Epic Fight are now cached completely in order to avoid wasting performance on fetching the camera instance every single tick.

## Configuration

The mod generates a `lockonmovementfix-client.toml` file in your main config folder. You can tune these values to fit exactly how you want it to feel:

* `turnSpeed` (default: 0.45): Controls how fast your character rotates while running. Lower values give you a smoother, wider turn arc.
* `idleTurnSpeed` (default: 0.70): Controls how fast your character turns to face the enemy when you stop moving. It is set a bit higher by default so your attacks actually land where you want them to.
* `lockOnRange` (default: 64): The maximum lock-on distance in blocks. 

## Requirements

* Minecraft Forge 1.20.1
* Epic Fight
* Better Lockon

## Building from source

If you want to build this project yourself:
1. Clone the repository
2. Run `gradlew build` in the root folder
3. The compiled jar will show up in the `build/libs` directory
