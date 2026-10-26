# QuietLapse v0.3 preview draft

Changes in this source package:
- Adds a live camera preview before starting a session.
- Tapping the foreground notification opens QuietLapse.
- Adds an in-app button to open the finished MP4 in an installed video player.
- Adds JPEG rotation metadata based on the display orientation.

Important limitations:
- Preview is released while the CaptureService uses the camera for background capture. It is not a live preview during capture yet.
- The folder-to-slideshow feature is not implemented in this draft.
- This is source code, not a built or device-tested APK. Build it with the existing GitHub Actions workflow and test carefully on the OnePlus 3T.
