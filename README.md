# QuietLapse v0.5 – Design & rotation

- Modern dark navy interface with teal, coral and indigo accents.
- Scrollable screen and compact camera preview retained.
- Screen rotation is handled without recreating MainActivity (`orientation|screenSize|keyboardHidden`), so UI values and active slideshow thread are not reset by a rotation.
- Added instance-state saving as a fallback for system recreation.
- CaptureService was intentionally left unchanged to protect stable background capture.

Build with the repository's GitHub Actions workflow. This source ZIP has not been built or tested on a physical OnePlus 3T in this environment.


v0.5.1 rettelser: preview genstartes efter rotation/tilladelser; optageknappen viser tydelig aktiv status; videoafspilning bruger FileProvider/chooser; knap til billedmappen; standardinterval 15 sekunder; FPS-indstillingen gælder både kameraoptagelser og timelapse fra billedmappe.


## QuietLapse Classic – preview-fix arbejdsbuild
Denne udgave erstatter previewets `SurfaceView` med `TextureView` og kobler Camera1 direkte til den aktuelle `SurfaceTexture`. Det er målrettet symptomet med sort forgrund, hvor kameraet tilsyneladende stadig leverer billeder. Det er en kodeændring, ikke en bekræftet løsning: APK skal bygges og testes på OnePlus 3T.

Andre ændringer fra 0.5.1 bevares: standardinterval 15 sekunder, FPS-valg, synlig optagelsesknap, FileProvider-afspilning og mappevalg.


## QuietLapse Classic v0.6.2 – progress

- Baseret direkte på brugerens v0.6.1-startup-fix kildekode.
- Viser procent og antal behandlede billeder under video-encoding, både for stop-af-optagelse og slideshow fra billedmappe.
- Viser også status under kopiering af billeder til midlertidig mappe.
- Ingen ændringer til kamera-previewets opbygning eller strømbesparelse i denne version.

Bemærk: Kildekoden er ændret, men er ikke bygget eller testet på en fysisk telefon i dette miljø. Byg via GitHub Actions og test først med en lille billedmappe.
