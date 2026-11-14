# Fearless × Yatagarasu: the files this app takes from the brand kit

The owner approved the Yatagarasu brand on 2026-11-10: the three-legged crow crest, the FEARLESS wordmark, and the
animated crow with its eagle-and-crow call. It replaces the winged-sun badge. The shared kit
(`fearless-yatagarasu-brand-kit`, generated from the design package `design/yatagarasu-20261110`, package r3) is not
committed anywhere; its `manifest.json` has SHA-256
`e49da29bbf91dc96d2afe3772b5ec50acc23bc28ff4af500aff0ff7f631ddb84` (kit v3, 2026-11-13). Kit v3 adds the launch clip, the
two transfer sounds, the haptic patterns, the solar geometry and the sky card for the owner's 2026-11-13 request ("make the
yatagarasu ignite animation the launching splash animation and zoom into the wallet … tactile feedback and sound effects
when sending/receiving tokens"); every file of kit v2 (`70771ec2…048b`, regenerated 2026-11-10 12:16) is byte-identical
in it.

The files below are byte-for-byte copies at their kit paths. `scripts/generate-brand-assets.py` pins each one and the
kit manifest, and derives every Android brand resource from them; `android-assets.json` records the hashes of what it
wrote. `scripts/test-brand-assets.py` checks the pins, that the committed outputs are what the generator writes, and that
every path is copied verbatim.

```
python3 scripts/generate-brand-assets.py                    # regenerate from these copies
python3 scripts/generate-brand-assets.py --kit <brand kit>  # refresh the copies from the kit first
python3 scripts/generate-avatar-brand.py                    # wallet avatar mask (runtime-utils CrestClip.kt)
python3 scripts/test-brand-assets.py
```

| Kit file | Android resource |
| --- | --- |
| `svg/android-adaptive-{foreground,background,monochrome}.svg` | `ic_launcher_{foreground,background,monochrome}` (108 dp adaptive icon, `main`, `release`; `debug` wears the gold crest `#FFC24A`) |
| `png/android/ic_launcher-<dpi>.png`, `ic_launcher_round-<dpi>.png` | legacy and round `mipmap-<dpi>` launcher icons (`main`, `release`) |
| `svg/android12-splash-icon.svg` | `fw_splash_icon`: the Android 12+ system splash, 288 dp, the whole crest inside the centre 192 dp circle, on ink `#120A2E` |
| `masters/fearless-lockup-stacked.svg` | `fw_brand_lockup`: full-page mark (PIN, welcome, onboarding start, About, launch art), about 56 % of the width, at most 280 dp |
| `svg/crest-badge.svg` | `fw_brand_badge`: round slots of 64 dp and more (the wallet hero's sync ring) |
| `svg/crest-badge-small.svg` | `fw_brand_badge_small`, `ic_wallet`, `ic_qr_code_logo`, and the wallet avatar mask |
| `masters/fearless-crest.svg`, `masters/fearless-crest-small.svg` | tintable crest marks `ic_fearless_logo` (64 dp), `ic_fearless_bird` and `ic_fearless` (24 dp) |
| `hero/welcome-idle.mp4`, `hero/welcome-caw.mp4`, `audio/caw.m4a` | `raw/fw_welcome_idle`, `raw/fw_welcome_caw`, `raw/fw_welcome_caw_sound` (feature-onboarding-impl) |
| `hero/welcome-poster.webp` | `drawable-nodpi/fw_welcome_poster`: the loop's first pose, and the whole hero under reduced motion |
| `tokens/colors.json` | checked against `design/fw.tokens.json`; the pinks follow spec item 9 via the kit's `tools/apply-tokens.mjs` (mark and glow `#E6006F`, button fill `#D6006A` → `#A8005A`, brand text and edges `#FF4FA3`), plus ink, night, plum, gold and text |
| `hero/launch-ignite.mp4`, `hero/launch-timeline.json` | `raw/fw_launch_ignite`, `raw/fw_launch_timeline` (`app`): the cold-start Ignite clip over the wallet host and its times |
| `audio/send.m4a`, `audio/receive.m4a` | `raw/fw_send`, `raw/fw_receive` (`common`): the transfer sounds |
| `tokens/haptics.json` | `raw/fw_haptics` (`common`): the haptic patterns timed to those sounds |
| `tokens/solar-geometry.json` | `raw/fw_solar_geometry` (`common`): the solar geometry drawn on a sent transfer and around the balance |
| `png/sky/sky-card.webp` | `drawable-nodpi/fw_sky_card` (`common`): the sunset sky behind the wallet balance |

The Play listing icon (`png/android/play-store-512.png`) is not in this repository, because no listing assets are; it is
uploaded in Play Console with the release.
