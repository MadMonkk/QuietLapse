# QuietLapse v0.4 source

Changes in this source package:
- Existing camera timelapse recording remains available and runs through the foreground service.
- Adds a live camera preview with a fixed portrait aspect ratio to avoid stretching the preview.
- Tapping the foreground notification opens QuietLapse.
- Adds an in-app button to play the latest completed MP4.
- Adds “VÆLG MAPPE OG LAV TIMELAPSE”: choose a folder using Android's folder picker and create an MP4 from JPG, JPEG, PNG, or WebP images directly inside that folder.
- Slideshow speed choices: 2, 5, 10, 15, or 24 frames per second. Images are sorted by filename and fitted into the output frame without stretching; different aspect ratios receive black bars.
- Slideshow output is saved under `Pictures/QuietLapse/Slideshow_<timestamp>/timelapse.mp4`.

This is source code, not a built or device-tested APK. Build using the GitHub Actions workflow (`Build QuietLapse APK`) and test on the OnePlus 3T. The slideshow currently reads images directly in the selected folder, not nested subfolders. Camera preview is released while the background capture service is using the camera.
