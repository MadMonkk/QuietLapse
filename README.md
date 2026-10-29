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


## v0.6.3 – orientation and night pause
- Before encoding, scans image dimensions and EXIF rotation for all images; landscape/portrait output follows the majority orientation. Images in the minority orientation are fitted with bars rather than cropped.
- Optional night pause in capture mode. Default window is 20:00–07:00; times can be changed in the app. During this window no photos are taken, and normal capture resumes after the end time. The app still keeps its camera service/foreground notification running; this feature prevents night photos but does not yet fully power down the camera.
- The slideshow/folder-to-video path uses the same orientation analysis.

Build with the existing GitHub Actions workflow. This source ZIP has not been built or tested on a physical OnePlus 3T; test a short session and a small mixed-orientation folder before relying on it for a long garden timelapse. Keep the previous working APK and original photos.


## QuietLapse Classic v0.6.4
- APK artifact is named `QuietLapse_Version_0.6.4.apk` in GitHub Actions.
- Captured images, session folders and generated timelapse videos are stored under `DCIM/QuietLapse`.
- Added a custom QuietLapse launcher icon.
- This source update has not been built or tested on a physical device in this environment.


## v0.6.5 – forsigtig strømbesparelsestest
- Kameraet åbnes ved hvert planlagt billede og frigives efter billedet er gemt.
- Under natpause frigives kameraet, og der tages ingen billeder.
- Bevarer billed-/videomappen DCIM/QuietLapse og eksisterende videofremdrift.
- Dette er en testversion: hyppig genåbning af kameraet kan give forsinkelse eller fejl på visse telefoner. Test først med en kort optagelse.
- APK-artifact: QuietLapse_Version_0.6.5.apk


## v0.6.6 – solpause og orientering
- Valgfri automatisk natpause beregnet ud fra seneste kendte placering og solopgang/solnedgang (beregnes lokalt uden internet).
- Hvis automatisk solpause er slået fra, bruges de manuelt valgte nat-tider som før.
- Videoens format vælges ud fra flertallet af billedernes faktiske EXIF-orientering; billeder drejes efter EXIF, så skift mellem lodret/vandret håndteres bedst muligt.
- Bygget til Android 9 / API 28. Denne kildepakke er ikke bygget eller testet på en fysisk telefon herfra.


## QuietLapse Classic v0.6.7 – hurtigere videoproduktion (forslag)

- Orienteringen vælges ud fra højst 200 jævnt fordelte billeder i stedet for at scanne alle billeder før encoding. Ved blandede billedretninger er valget derfor et repræsentativt estimat, ikke en fuld optælling.
- Billeder dekodes med `inSampleSize`, når kildens opløsning er væsentligt større end den endelige videoramme, for at reducere hukommelsesforbrug og pixelbehandling.
- Den eksisterende H.264-encoder, 1080p-grænse, billedtilpasning og fremdriftsprocent er bevaret.

Denne optimeringskilde er ikke bygget eller testet på en fysisk Android 9-enhed. Test først med 100–300 kopier af billeder, og sammenlign tid, billedretning og billedkvalitet med den tidligere version.
