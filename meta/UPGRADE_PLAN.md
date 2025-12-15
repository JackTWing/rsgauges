# Upgrade Plan: Forge 1.20.1 -> NeoForge 1.21.1

1. **Build tooling refresh**
   - Replace ForgeGradle with the NeoGradle userdev plugin and update Gradle wrapper to the version shipped with the 1.21.1 NeoForge MDK (Gradle 8.14.3).
   - Update `settings.gradle`, `gradle.properties`, and `build.gradle` to target Minecraft 1.21.1, Java 21, and the desired NeoForge version. Configure repositories and resource processing per the NeoForge MDK template.

2. **Metadata and dependency migration**
   - Replace `META-INF/mods.toml` with `META-INF/neoforge.mods.toml`, adjusting dependency declarations for NeoForge and Minecraft 1.21.1.
   - Update optional dependencies such as JEI to their 1.21.1 NeoForge coordinates and refresh any other metadata values (version ranges, description formatting, etc.).

3. **Initialization & registry updates**
   - Refactor `ModRsGauges` to the NeoForge initialization style using constructor-injected `IEventBus`/`ModContainer`, and switch from `MinecraftForge` to `NeoForge` event bus APIs.
   - Update registry helpers (`Registries`, `ModContent`, etc.) to use the new `net.neoforged` packages and registration helpers, ensuring creative tabs and deferred registers align with 1.21.1 expectations.

4. **API package migrations**
   - Replace all `net.minecraftforge` imports with their `net.neoforged` equivalents (dist markers, events, networking, tags, capabilities, crafting helpers, FakePlayer, etc.).
   - Adjust for behavioral changes such as `BuildCreativeModeTabContentsEvent` using `getTabKey`, NeoForge capability constants, and any renamed helper methods or factories.

5. **Validation**
   - Re-run `./gradlew --refresh-dependencies`/`./gradlew build` to confirm the project compiles under NeoForge 1.21.1.
   - Address compiler or data-generation issues uncovered during the build to ensure a clean workspace.
