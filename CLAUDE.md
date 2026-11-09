# Pleiades
Android-tablet 2D asset tool (Kotlin, Compose, pkg `com.pleiades.app`). Pixel/Raster/Vector sprites, animation, export.

## Rules
- Work on `main` only. No branches/PRs. Commit + push to main.
- No README or usage guide. Keep this file short; update the Map when files change.
- Be terse. Don't re-read files you already know from the Map; grep, then read ranges.
- No Android SDK/network builds here: CI (`.github/workflows/build.yml`, gradle 8.9 `assembleDebug`) is the compiler. After push: `gh run list -L1`, then `gh run view <id> --log-failed | grep '^e: '`.
- Stack: AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, minSdk 26, compileSdk 35. No wrapper; don't add deps unless needed.
- Commits: short imperative message + trailers from the session reminder.

## Map (`app/src/main/java/com/pleiades/app/`)
- `MainActivity.kt` PleiadesApp: layout, SAF pickers (tree=export dir, create/open=.pls), save/open/export wiring, snackbar.
- `core/` (no UI):
  - `Doc.kt` Sprite (layers shared; Frame = cel Bitmap per layer + vecs), undo/redo snapshots, composite(), frame/layer ops, centreLayer, palette(); view zoom/pan + fileUri live on Sprite.
  - `Painter.kt` Tool, ToolState, Painter (begin/move/end/cancel): pen/eraser/rect/ellipse/fill/picker. Pixel = mask stamping; raster = restore snapshot + redraw path; vector = VObj list.
  - `Vec.kt` VObj (VStroke, VShape), Vec.render, SVG.
  - `Export.kt` Fmt/Mode/ExportOpts, sheet (+json), per-frame, SAF write. `Gif.kt` own GIF89a+LZW encoder.
  - `Project.kt` .pls (zip: meta.json + PNG per cel), image import.
- `ui/`: `AppState.kt` (tabs, tool settings, Dlg), `CanvasView.kt` (draw + 1-finger paint, 2-finger pan/zoom), `Bars.kt` (ToolBar, TopBar tabs, Timeline/onion/playback), `Panels.kt` (SidePanel: colour/palette, layers), `ColorWheel.kt`, `Dialogs.kt` (new/export/close/rename), `Widgets.kt`, `Theme.kt` (`P` colours).

## Conventions
- Compose state: mutate Sprite then `sp.touch()` (bumps `rev`) to redraw. Structural edits call `clearHistory()`.
- Colours are ARGB Ints; palette is derived from used pixels (bucketed for non-pixel kinds).
- `Centre` tool = centres current layer's content on canvas (action, not a mode).
- Known gaps: no autosave/session restore, no selection/move tool, no symmetry.
