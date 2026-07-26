# High-contrast collapsed grip design

## Context

The compact collapsed grip currently uses a near-white fill at alpha 148 and a white outline at
alpha 94. It becomes difficult to see over light application surfaces, especially while the user
previews edge distance and watches the grip move.

## Approaches

1. **Dark graphite fill with a cyan outline — selected.** The dark core contrasts with light
   surfaces, while the bright cool outline preserves separation on dark surfaces and matches the
   existing portal icon accent.
2. **Only increase dark fill opacity.** Clear on light surfaces but weak on dark surfaces.
3. **Only increase white opacity.** Clear on dark surfaces but still weak on white and pale gray.

## Behavior and implementation

- Keep compact grip geometry at 4 x 48 dp and touch geometry at 10 x 64 dp.
- Use an opaque-enough graphite/navy fill and a dedicated sub-1 dp cyan outline paint.
- Use the same colors at every edge distance and during live preview, so movement never changes
  perceived visibility.
- Do not add glow, shadow, pulsing, width changes, background sampling, or new settings.
- Leave the larger standard edge handle unchanged to avoid increasing its visual weight on
  devices that do not use the compact compatibility grip.

## Verification

Run all unit tests, Android Lint, debug assembly, and whitespace checks. Install on the connected
phone and inspect screenshots over the light settings UI and launcher at 0 dp and 20 dp. Verify
geometry, live distance movement, launcher/application expansion, outside collapse, Back outside
the grip, persistence, and error logs remain unchanged.

