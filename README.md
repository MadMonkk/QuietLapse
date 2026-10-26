# QuietLapse v0.4.1 – scroll- og previewrettelse

Denne version bygger videre på v0.4 med timelapse fra en eksisterende billedmappe.

Rettelser:
- Hele skærmens indhold ligger nu i en lodret ScrollView, så knapper og status kan nås ved at rulle.
- Kamerapreview har en begrænset højde på 220 dp, så det ikke skubber resten af appen væk.
- Previewets mål respekterer nu layoutets højdebegrænsning.

Byg APK via GitHub Actions-workflowet i `.github/workflows/build.yml`.
Bemærk: Kildekoden er ændret, men APK er ikke bygget eller testet på en fysisk telefon i denne session.
