# QuietLapse – første testversion (Android 9)

Dette er et Android Studio/Gradle-projekt, der er sat op til at blive bygget med GitHub Actions.
Det er en tidlig prototype – ikke en færdigtestet APK.

## Funktioner i første version
- Vælg interval i sekunder (minimum 10 sekunder) eller minutter.
- Start/stop en timelapse-optagelse.
- Optager med bagkamera via Camera1 og en foreground service, så optagelsen kan fortsætte med skærmen slukket.
- Billeder gemmes i `Pictures/QuietLapse/<optagelse>/`.
- Ved Stop forsøger appen at samle JPEG-billeder til MP4 med Androids MediaCodec.

## Byg via GitHub Actions
1. Opret et nyt GitHub-repository, fx `QuietLapse`.
2. Upload indholdet af denne ZIP (mapperne og filerne – ikke selve ZIP-filen).
3. Gå til **Actions** og kør workflowet `Build QuietLapse APK`.
4. Download artifact `quietlapse-debug-apk` og installér APK'en på telefonen.

## Bemærk
Denne første prototype skal testes på din OnePlus 3T / Android 9. Kameraadgang i baggrunden kan variere efter systemindstillinger.
Hvis MP4-genereringen fejler eller telefonen ikke understøtter encoder-formatet, gemmes billederne stadig, og der vises en fejlmeddelelse.
