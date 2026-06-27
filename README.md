[🇳🇱 Nederlands](README.md) | [🇩🇪 Deutsch](README.de.md) | [🇬🇧 English](README.en.md)

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Platform: Android 8+](https://img.shields.io/badge/Android-8.0%2B-green.svg)]()
[![Wear OS 3+](https://img.shields.io/badge/Wear%20OS-3%2B-teal.svg)]()

# VeloGappie

Onafhankelijke Android-app voor Veloretti V2 e-bikes.
Geen account nodig — verbindt rechtstreeks met je fiets via Bluetooth.

## Download

Kant-en-klare APK's staan op de [Releases-pagina](../../releases/latest).
Geen Play Store-account nodig — gewoon downloaden en installeren.

## Wat het is

VeloGappie communiceert rechtstreeks met je fiets via Bluetooth — geen Veloretti-account,
geen cloudafhankelijkheid. Je ritgegevens blijven op je telefoon. Optionele functies
zoals weer op het fietsdisplay en Google Drive-backup gebruiken het netwerk, maar er
gaat niets naar Veloretti's servers.

## Wat het niet is

VeloGappie is geen officieel Veloretti-product en geen vervanging van de officiële app.
Er is geen garantie dat alles correct werkt. Gebruik is op eigen risico.

## Screenshots

| Dashboard | Ritgeheugen | Instellingen | Informatie |
|-----------|-------------|--------------|------------|
| ![Dashboard](docs/assets/screenshot-dashboard.png) | ![Ritgeheugen](docs/assets/screenshot-ridehistory.png) | ![Instellingen](docs/assets/screenshot-settings.png) | ![Informatie](docs/assets/screenshot-info.png) |

| Wear OS |
|---------|
| ![Wear OS](docs/assets/wearos-screenshot.png) |

## Waarom VeloGappie?

| | Officiële Veloretti-app | VeloGappie |
|---|---|---|
| Account vereist | Ja | Nee |
| Veloretti-cloud vereist | Ja | Nee |
| Ritgeschiedenis met GPS | Nee | Ja |
| Wear OS-ondersteuning | Nee | Ja |
| Health Connect | Nee | Ja |
| Navigatie naar fietsdisplay | Beperkt | Google Maps, Komoot, Waze |
| Open source | Nee | Ja (GPL-3.0) |

## Features

### Besturing
- **Trapondersteuning** niveau 0–5 (inclusief Superhero-modus)
- **Verlichting** — handmatig aan/uit, of automatisch bij zonsondergang
- **Digitale bel** (standaard- of pinggeluid)
- **Cadanscalibratie** — exacte 1-rpm-stappen via slider

### Enviolo-naaf
- Eco / Comfort / Sport rijmodi
- Startmultiplier instellen (0,00–2,55×)
- Naafdiagnostiek — serienummer, artikelnummer, cadansbereik, naaf-kilometerstand

### Fietsdisplay
- **Klok** — tijd op het display in normaal of groot tweevelden-formaat
- **Hartslag** — live BPM vanaf het horloge, of afwisselend met klok
- **Live weer** — temperatuur, neerslag en icoon naar het fietsdisplay
- **Navigatiepijlen** met afstand in meters naar het fietsdisplay sturen
- **Vrije tekst** — maximaal 10 tekens op het display (bijv. straatnaam)

### Data
- Accupercentage, gezondheid, oplaadstatus
- Snelheid, maximumsnelheid, cadans
- Kilometerstand en tripafstand
- Firmwareversies (display + experience-module)

### Ritgeheugen
- Lokale opslag van ritten (afstand, duur, gemiddelde/maximale snelheid, hartslag, cadans)
- **GPS-routes** — per rit wordt de route gelogd en als kaart weergegeven in de ritdetails
- **Stijging en accu** — hoogtemeters en accuverbruik (start → eind) per rit
- **Ritgroepen** — ritten bundelen (bijv. fietsvakantie, woon-werkverkeer)
- Ritten samenvoegen, verwijderen, aan groepen toevoegen/verwijderen
- **Health Connect** — schrijft automatisch fietssessies naar Health Connect met directe link vanuit ritdetails
- **Google Drive-backup** — optionele synchronisatie van instellingen en ritgeschiedenis

### Navigatiebrug
- Stuurt afslaginstructies van Google Maps, Komoot, Waze, OsmAnd en meer door naar het fietsdisplay
- Automatische herkenning van richting (links/rechts/rechtdoor/keren) en afstand
- Meertalige ondersteuning (NL/EN/DE)

### Launch control
- Lang indrukken van de stuurknop zet trapondersteuning en Enviolo-ratio op maximaal
- Schakelt automatisch terug zodra je topsnelheid bereikt

### Wear OS-companion
- Live snelheid, afstand, duur, hartslag op je pols
- Cadansregeling via de kroon van het horloge
- Dubbelklik om verlichting te schakelen
- Ambient always-on modus
- Hartslagsensor op het horloge

## Installatie

Dit is een standaard Android Studio-project (Kotlin + Jetpack Compose).

1. Open de map `app/` in Android Studio
2. Laat Gradle synchroniseren
3. Build & run op een telefoon met Android 8.0+ (API 26)
4. Voor de horlogeapp: build de `:wear`-module en installeer op een Wear OS 3+-apparaat

Beide modules delen een vastgezette `debug.keystore` zodat de Wear OS Data Layer ze aan
elkaar kan koppelen. Dit is een standaard Android-debugsleutel (wachtwoord: `android`),
geen geheim.

**Vereisten:** Android SDK 34, JDK 17, Gradle 8.7+

## Gebruik

1. Open de app en geef Bluetooth- en locatiepermissies
2. Tik op **Scannen naar fietsen** — je fiets verschijnt als `V-<serienummer>`
3. Tik op je fiets om te verbinden
4. Het dashboard toont direct accuniveau, snelheid en besturingsmogelijkheden
5. Veeg naar de andere tabbladen voor ritgeschiedenis, instellingen en technische info

Locatiepermissie wordt alleen gebruikt voor de automatische verlichtingsfunctie
(zonsondergangberekening). Je locatie wordt nergens naartoe gestuurd.

## Compatibiliteit

Bedoeld voor Veloretti V2 e-bikes die over BLE adverteren als `V-<serienummer>`.
V1-fietsen gebruiken een ander protocol en worden niet ondersteund.

## Bijdragen

Issues en pull requests zijn welkom. Als je een bug vindt of een feature wilt toevoegen,
open dan een issue zodat we het eerst kunnen bespreken.

## Licentie

GPL-3.0 — zie [LICENSE](LICENSE).

## Disclaimer

Deze app is **niet geassocieerd met en niet goedgekeurd door Veloretti B.V.** Gebruik is
volledig op eigen risico. De ontwikkelaar is niet aansprakelijk voor schade aan de fiets,
verlies van garantie of softwarefouten veroorzaakt door Bluetooth-commando's.

"Veloretti" en "Ivy" zijn handelsmerken van Veloretti B.V. "Enviolo" is een handelsmerk
van Enviolo. Alle merknamen zijn eigendom van hun respectieve eigenaars.

## Lettertype

Gebruikt [Hanken Grotesk](https://fonts.google.com/specimen/Hanken+Grotesk)
(SIL Open Font License).
