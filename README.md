# QuietLapse v0.5 – Design & rotation

- Modern dark navy interface with teal, coral and indigo accents.
- Scrollable screen and compact camera preview retained.
- Screen rotation is handled without recreating MainActivity (`orientation|screenSize|keyboardHidden`), so UI values and active slideshow thread are not reset by a rotation.
- Added instance-state saving as a fallback for system recreation.
- CaptureService was intentionally left unchanged to protect stable background capture.

Build with the repository's GitHub Actions workflow. This source ZIP has not been built or tested on a physical OnePlus 3T in this environment.


v0.5.1 rettelser: preview genstartes efter rotation/tilladelser; optageknappen viser tydelig aktiv status; videoafspilning bruger FileProvider/chooser; knap til billedmappen; standardinterval 15 sekunder; FPS-indstillingen gælder både kameraoptagelser og timelapse fra billedmappe.
