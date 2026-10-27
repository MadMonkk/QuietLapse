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


## QuietLapse Classic v0.7 – strøm og opløsning
- Valg af høj, mellem (~3 MP) eller lav (~1 MP) fotoopløsning; standard er højeste understøttede opløsning.
- Valgfri strømbesparelse stopper kamera-preview mellem billeder og starter det igen kort før næste optagelse.
- Viser procent og antal behandlede billeder under videodannelse.
- Denne kildekode er ændret, men APK er ikke bygget eller testet på en fysisk telefon i denne levering. Test først en kort optagelse med 10-20 billeder, og kontrollér at billederne gemmes korrekt, før en lang natteoptagelse.
