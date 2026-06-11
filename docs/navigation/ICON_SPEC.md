# EZmap Navigation Maneuver Icon Specification

## Background

The navigation HUD uses `TurnInstructionCard` with a 64x64dp icon box. Currently arrows
are drawn at runtime via `TurnArrowCanvas.kt` (Compose Canvas). This spec describes the
equivalent static VectorDrawable XML files that can replace or supplement those canvas
drawings, and that can also be used in notifications, widgets, or other contexts that
cannot render Compose.

`Guide.type` (from Kakao Mobility `directions/v1/car` response) maps directly to the
`directionCode` field in `TurnDirectionUi`. The currently handled codes and their
canonical meanings are:

| Code | Kakao meaning        | TurnArrowCanvas function |
|------|----------------------|--------------------------|
| 0    | 직진 (Go straight)   | `drawStraight`           |
| 1    | 좌회전 (Turn left)   | `drawLeftTurn`           |
| 2    | 우회전 (Turn right)  | `drawRightTurn`          |
| 3    | U턴 (U-turn)         | `drawUTurn`              |
| 5    | 고가도로 진입        | `drawElevated`           |
| 6    | 지하차도 진입        | `drawUnderground`        |
| 11   | 출발 (Depart)        | `drawStraight` (fallback)|
| 12   | 도착 (Arrive)        | `Icons.Filled.Flag` (Compose) |
| 16   | 왼쪽 분기 (Fork L)   | `drawLeftFork`           |
| 17   | 오른쪽 분기 (Fork R) | `drawRightFork`          |

All other codes fall back to `drawStraight`.

---

## Color Palette

| Token       | Hex       | Usage in icons                          |
|-------------|-----------|-----------------------------------------|
| Cyan accent | `#4FC3F7` | Default stroke color (idle/far state)   |
| Warning     | `#FFC107` | Stroke color injected at ≤100m (runtime)|
| Alert       | `#E94560` | Stroke color injected at ≤50m (runtime) |
| Transparent | —         | Icon background (card provides bg)      |

The XML files use Cyan `#4FC3F7` as the canonical stroke color. The runtime color is
applied by tinting the drawable in code, so **do not hard-code Warning/Alert colors
inside the XML**.

---

## Icon Geometry Rules

- **Canvas size:** 48x48dp (XML `android:width="48dp" android:height="48dp"`,
  `android:viewportWidth="48"`, `android:viewportHeight="48"`)
- **Stroke width:** 6 (equivalent to ~3dp at 2x; 13% of canvas width matching
  `TurnArrowCanvas` ratio `size.width * 0.13f`)
- **Stroke cap:** `round`
- **Stroke join:** `round`
- **Fill:** none on all path elements (stroke-only / line-art)
- **Background:** transparent (`android:background` not set; card provides it)
- **Coordinate origin:** top-left (0,0), bottom-right (48,48)
- **Safe zone:** 4px margin all sides → usable area is (4,4)→(44,44)

Derived reference points (matching `TurnArrowCanvas` proportions):
```
cx      = 24          // horizontal center
top     = ~5.8        // height * 0.12
bottom  = ~42.2       // height * 0.88
midY    = ~24         // height * 0.50
leftX   = ~8.6        // width * 0.18
rightX  = ~39.4       // width * 0.82
ah      = ~10.6       // width * 0.22  (arrowhead arm half-length)
r       = ~9.6        // width * 0.20  (curve radius)
```

---

## File Naming Convention

```
ic_maneuver_{type}.xml
```

| File name                      | directionCode | Maneuver            |
|--------------------------------|---------------|---------------------|
| `ic_maneuver_straight.xml`     | 0, 11, fallback | 직진 / 출발        |
| `ic_maneuver_turn_left.xml`    | 1             | 좌회전              |
| `ic_maneuver_turn_right.xml`   | 2             | 우회전              |
| `ic_maneuver_uturn.xml`        | 3             | U턴                 |
| `ic_maneuver_elevated.xml`     | 5             | 고가도로 진입       |
| `ic_maneuver_underground.xml`  | 6             | 지하차도 진입       |
| `ic_maneuver_arrive.xml`       | 12            | 도착                |
| `ic_maneuver_fork_left.xml`    | 16            | 왼쪽 분기           |
| `ic_maneuver_fork_right.xml`   | 17            | 오른쪽 분기         |

---

## Per-Icon Shape Description

All icons share the same VectorDrawable shell:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="48"
    android:viewportHeight="48">
    <!-- paths here -->
</vector>
```

Use `android:strokeColor="#4FC3F7"`, `android:strokeWidth="6"`,
`android:strokeLineCap="round"`, `android:strokeLineJoin="round"`,
`android:fillColor="@android:color/transparent"` on every `<path>`.

---

### ic_maneuver_straight.xml — 직진 / 출발

Vertical line from bottom to top, arrowhead pointing up.

```
Shaft:    M 24 42 L 24 6
Left arm: M 24 6 L 13.4 16.6
Right arm:M 24 6 L 34.6 16.6
```

Visual: a straight upward arrow centered in the viewport.

---

### ic_maneuver_turn_left.xml — 좌회전

Shaft rises from bottom-center, curves 90° left via quadratic Bezier, horizontal
segment exits left, arrowhead points left.

```
Path (shaft + curve + horizontal):
  M 24 42
  L 24 33.6
  Q 24 24, 14.4 24
  L 8.6 24

Arrowhead (left-pointing, at (8.6, 24)):
  M 8.6 24 L 19.2 13.4
  M 8.6 24 L 19.2 34.6
```

Note: arrowhead arms point right (away from the tip direction), i.e., the arm lines
go from the tip `(8.6, 24)` toward `(8.6 + ah, 24 - ah)` and `(8.6 + ah, 24 + ah)`.

---

### ic_maneuver_turn_right.xml — 우회전

Mirror of turn_left.

```
Path:
  M 24 42
  L 24 33.6
  Q 24 24, 33.6 24
  L 39.4 24

Arrowhead (right-pointing, at (39.4, 24)):
  M 39.4 24 L 28.8 13.4
  M 39.4 24 L 28.8 34.6
```

---

### ic_maneuver_uturn.xml — U턴

Two vertical lines joined at the top by a 180° arc, arrowhead on the right shaft
pointing downward.

```
Left shaft:  M 14.4 42 L 14.4 18.2   (topY + r = 8.6 + 9.6 = 18.2)
Arc (top):   arcTo rect(14.4, 8.6, 33.6, 27.8), startAngle=180, sweep=-180
Right shaft: L 33.6 42

Arrowhead (downward, at (33.6, 42)):
  M 33.6 42 L 23 31.4
  M 33.6 42 L 44.2 31.4
```

Arc center-x = (14.4 + 33.6)/2 = 24, center-y = (8.6 + 27.8)/2 = 18.2, rx = ry = 9.6.
In VectorDrawable path data: `a 9.6,9.6 0 0 1 19.2,0` (half-circle clockwise).

Full path data:
```
M 14.4 42 L 14.4 18.2 A 9.6 9.6 0 0 1 33.6 18.2 L 33.6 42
```

Arrowhead lines added as separate paths.

---

### ic_maneuver_elevated.xml — 고가도로 진입

Straight arrow (same as `ic_maneuver_straight.xml`) plus a small arc to the upper-right
of the arrowhead indicating an overpass ramp. The secondary arc is drawn at 55% opacity.

```
[Base] Same as straight:
  Shaft: M 24 42 L 24 6
  Left arm: M 24 6 L 13.4 16.6
  Right arm: M 24 6 L 34.6 16.6

[Ramp indicator] Small quadratic arc to upper-right (alpha 0.55):
  M 30.3 12.3 Q 39.4 6 39.4 12.3
```

The ramp indicator path uses `android:strokeAlpha="0.55"`.

---

### ic_maneuver_underground.xml — 지하차도 진입

Straight arrow plus a small arc below to the lower-right indicating a tunnel entry.
Secondary arc at 55% opacity.

```
[Base] Same as straight.

[Tunnel indicator] Small quadratic arc to lower-right (alpha 0.55):
  M 30.3 35.7 Q 39.4 42 39.4 35.7
```

---

### ic_maneuver_arrive.xml — 도착

A downward arrow (shaft from top, pointing down) combined with a horizontal baseline
to represent landing/arrival. Alternatively, a flag-pole with a triangular flag is
acceptable and more recognizable.

**Flag-pole option (recommended — matches existing Compose `Icons.Filled.Flag` fallback):**

```
Pole (vertical line):
  M 16 8 L 16 42

Flag triangle (filled or stroked):
  M 16 8 L 38 16 L 16 24 Z
```

Use `android:fillColor="#4FC3F7"` and `android:fillAlpha="0.85"` on the triangle path,
with no stroke. The pole uses stroke only.

---

### ic_maneuver_fork_left.xml — 왼쪽 분기

Main straight arrow up the center, plus a secondary thinner branch forking to the
upper-left at mid-height. The branch arrow is the emphasized one (highlighted).

```
[Main shaft — full weight]:
  M 24 42 L 24 6
  Arrowhead: M 24 6 L 13.4 16.6 / M 24 6 L 34.6 16.6

[Left branch — 75% stroke width = 4.5]:
  Branch line: M 24 24 L 9.6 10.6
  Branch arrowhead:
    M 9.6 10.6 L 19.9 13.9
    M 9.6 10.6 L 19.9 7.3
```

The branch stroke width is 4.5 (75% of 6). Use a separate `<path>` with
`android:strokeWidth="4.5"`.

---

### ic_maneuver_fork_right.xml — 오른쪽 분기

Mirror of fork_left.

```
[Main shaft — full weight]: same as straight.

[Right branch — 75% stroke width = 4.5]:
  Branch line: M 24 24 L 38.4 10.6
  Branch arrowhead:
    M 38.4 10.6 L 28.1 13.9
    M 38.4 10.6 L 28.1 7.3
```

---

## Kotlin Helper Function Spec

Add this function to `TurnArrowCanvas.kt` or a new file
`android/app/src/main/java/com/example/ez_capstone/ui/components/navigation/ManeuverIconRes.kt`:

```kotlin
package com.example.ez_capstone.ui.components.navigation

import androidx.annotation.DrawableRes
import com.example.ez_capstone.R

/**
 * Maps a Kakao Mobility Guide.type (directionCode) to its VectorDrawable resource ID.
 *
 * Returns R.drawable.ic_maneuver_straight for any unrecognised code.
 */
@DrawableRes
fun maneuverIconRes(directionCode: Int): Int = when (directionCode) {
    0    -> R.drawable.ic_maneuver_straight     // 직진
    1    -> R.drawable.ic_maneuver_turn_left    // 좌회전
    2    -> R.drawable.ic_maneuver_turn_right   // 우회전
    3    -> R.drawable.ic_maneuver_uturn        // U턴
    5    -> R.drawable.ic_maneuver_elevated     // 고가도로
    6    -> R.drawable.ic_maneuver_underground  // 지하차도
    11   -> R.drawable.ic_maneuver_straight     // 출발 (same shape as straight)
    12   -> R.drawable.ic_maneuver_arrive       // 도착
    16   -> R.drawable.ic_maneuver_fork_left    // 왼쪽 분기
    17   -> R.drawable.ic_maneuver_fork_right   // 오른쪽 분기
    else -> R.drawable.ic_maneuver_straight     // 미분류 → 직진 fallback
}
```

**Usage in Compose** (to use the static drawable instead of TurnArrowCanvas):

```kotlin
// Option A: keep TurnArrowCanvas for live tinting (current approach — no change needed)

// Option B: use static VectorDrawable with tinting
@Composable
fun ManeuverIcon(directionCode: Int, tint: Color, modifier: Modifier = Modifier) {
    if (directionCode == 12) {
        Icon(Icons.Filled.Flag, contentDescription = null, tint = tint, modifier = modifier)
    } else {
        Icon(
            painter = painterResource(id = maneuverIconRes(directionCode)),
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    }
}
```

---

## Generation Notes for Codex

1. Generate one `.xml` file per row in the "File Naming Convention" table.
2. Validate each file parses as valid Android VectorDrawable XML (root element `<vector>`).
3. The `drawLeftTurn` / `drawRightTurn` arrowhead geometry: the two arm lines originate
   AT the arrowhead tip and point BACK toward the main shaft — not away from it.
   This matches `TurnArrowCanvas.kt` lines 66-67 and 83-84.
4. For `ic_maneuver_uturn.xml`, the arc direction is **counter-clockwise** when viewed
   on screen (the `sweep=-180` in `TurnArrowCanvas` line 100). In SVG/path data this is
   `A rx ry 0 0 1 ex ey` (large-arc=0, sweep=1 in SVG convention = clockwise in
   screen coords where Y increases downward).
5. `ic_maneuver_arrive.xml`: the flag pole starts at (16,8) not center, to leave room
   for the flag body to the right. This intentionally matches the visual weight of
   `Icons.Filled.Flag` which is used as the Compose fallback (code 12 in
   `TurnInstructionCard.kt` line 105).
6. Do NOT add `android:tint` inside the XML. Tinting is applied at runtime by Compose
   `Icon(tint = urgencyColor)` and the urgency color changes with distance.
