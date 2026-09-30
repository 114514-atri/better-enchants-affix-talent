# Better Enchants — Affix, Talent & Difficulty

Open-source affix, talent/constitution, and world difficulty systems.

This repository is an **open-source source extract** of original systems from the author's Better Enchants family.
Same-author projects such as [yandere](https://github.com/114514-atri/yandere) may be referenced.
References to this author's original private content (for example Scarlet realm or sacred stations) are **optional soft hooks** — they activate only when that private companion content is present, otherwise they no-op.

## License

MIT — see [LICENSE](./LICENSE).

## Layout

- `src/main/java/com/betterenchants/...` — server/common logic for this module
- `src/client/java/...` — client pieces when present
- `src/main/resources/...` — datapack / assets belonging to this module
- `src/main/java/com/betterenchants/compat/OptionalPrivateHooks.java` — optional detection bridge (safe to delete in forks)

## Soft hooks

Call sites that previously hard-referenced private boss / dimension / spell packages now go through `OptionalPrivateHooks`.
If you fork this repo and do not care about private companion content, delete the hook class and related branches.

## Not included

Ported or third-party-derived private pack content is **not** published here.
Machine-local paths and personal environment files are stripped.

## Stats (extract)

- Java files: 37
- Resource files: 0
- Client Java files: 19

## Build note

These trees are published as **readable open sources** aligned to Fabric 1.21.x / `better_enchants` packaging.
Wiring into a runnable mod jar still requires a host `fabric.mod.json` / registry bootstrap (private full pack or your own fork entrypoint).
