# Noise Timer

A small, open-source Android app that plays white / pink / brown noise
with an optional sleep timer (with fade-out).

A free alternative to the various paid "noise + sleep timer" apps on the Play Store. 
No accounts, no ads, no network access, no tracking. It is by design, 
super simple and will be kept it that way.

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="280" alt="Noise Timer home screen">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="280" alt="Noise Timer playing brown noise with a sleep timer set">
</p>

## Features

- White, pink, and brown noise, generated in real time (not looped
  samples, so there's no audible seam/click).
- Adjustable volume.
- Sleep timer with presets (5/15/30/45/60/90/120 minutes) or a custom
  duration, with an optional 20-second fade-out before it stops.
- Playback continues in the background via a foreground service, with
  play/pause/stop controls in the notification.
- Settings (noise type, volume, timer, fade-out) are remembered between
  launches.

## Installation

Download the APK on the releasses or better install nad keep it updated via [f-droid](https://f-droid.org/en/packages/com.chmouel.noisetimer/)

## License

MIT — see [LICENSE](LICENSE).
