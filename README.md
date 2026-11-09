# Yahh's Ping Mod (Fabric, client-side)

Look at a block (or a player) and press **V** (rebind in Controls > "Yahh's Ping Mod").

Your trusted teammates see this in their chat:

    Yahh marked Grass Block 141.2 blocks away (-1960.0 65.0 -717.1)

and a marker on screen (the pinger's head, "<Name>'s Marker", and a diamond on the spot) plus a sound.

- Pinging a player shows their name in green instead of a block.
- If the ping is in a different dimension there is no marker or sound, just the chat line, which ends with
  "in The Nether" etc.
- No distance limit (the client can only see loaded chunks, so in practice it is your render distance).
- The marker stays for 30 seconds.

## How it is sent
One normal chat message per ping (not one /msg per teammate), at most one every 1.25 seconds, so mashing the key
cannot trip the server's spam kick. The message is encrypted with a team key, so to anyone without the mod it is just
`YPM1:...`, never coordinates. Teammates with the mod never see that raw text.

## Setup (every teammate)
1. Install the jar for your Minecraft version + Fabric API.
2. `/ypm add <name>` for each teammate you trust (you only see pings from people on your list).
3. Optional but recommended: everyone runs the same `/ypm key <secret>` so outsiders can't read your pings.

Commands: `/ypm add <player>`, `/ypm remove <player>`, `/ypm list`, `/ypm key [secret]` (`/yahhsping` works too).
Settings are in `config/yahhsping.json` (trusted list, key, `cooldownMs`). The old Ping Trusted list is imported automatically.

## Versions
One jar per Minecraft version: 1.21.11, 26.1 (also 26.1.x), 26.2 (also 26.2.x), 26.3 and newer.
The 26.3 jar is allowed to load on newer versions, but Minecraft may change things again, so rebuild if one breaks.

## Building (GitHub does it for you)
`.github/workflows/build.yml` builds all four versions. Open the Actions tab, open the latest run, and download the
`yahhs-ping-mod-<version>` artifact for your version.
Locally: JDK 25, then `./gradlew -Pmc=1.21.11 build` (or 26.1 / 26.2 / 26.3). The jar is in `build/libs/`.

## Layout
- `src/main/java` - all the mod logic (same for every version)
- `src/compat/a` - 1.21.11 versions of the few calls that changed
- `src/compat/b` - 26.1 and 26.2
- `src/compat/c` - 26.3 (keybinds switched to SDL)
