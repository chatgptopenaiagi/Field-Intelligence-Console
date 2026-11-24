# Mission 003 engineering recovery

The owner resumed development on 2026-11-24. Field Intelligence Console originated in the Manus AI cloud environment. The Android entry point remains one central SCAN button. A foreground location fix opens the observation dialog, and OPEN MATRIX saves the observation and opens its functions. Tools also provides explicit coordinate entry and offline emergency numbers when GPS is unavailable.

The published baseline is [beta7](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.7), source `8987a0c894dba9002cde1820e9e185e3766ef865`. The preceding [handoff](MISSION_002.md) records its 11 release assets. Mission 003 uses the existing private repository and `v4-development`. Development packages identify themselves as `4.0.0-beta.8.dev1`, wheel `4.0.0b8.dev1`, Android code 11, Windows numeric version 4.0.0.8.

## Matrix and external actions

| Function | What happens after selection |
| --- | --- |
| Info Scan | Opens a place-information search or previews a location-specific AI request. |
| History | Opens historical reference searches or previews a history request. The toolbar history opens saved observations internally. |
| Navigation | Opens the selected coordinates with a maps application or browser. |
| AI Analysis | Previews the exact text before the user selects the ChatGPT share or copy-and-paste fallback. |
| Risk / Safety | Opens a search for public services or previews a request for sourced safety information. |
| Logistics | Searches for travel amenities near the selected point. |
| Financial | Opens a place-specific economic search or previews the financial request. |
| Emergency Exit | Opens the offline catalog. Nearby OpenStreetMap lookup is a separate user action. Selecting a verified number opens the OS dialer. |
| Google Earth | The dark-blue globe opens an Earth coordinate search targeted to the Google Earth package. If unavailable, the user can choose Earth Web or installation. |

The canonical Android components are `ui/main/MainScreen.kt`, `viewmodel/MainViewModel.kt`, `location/LocationManager.kt`, `intent/MatrixHandoff.kt`, `prompt/FieldPromptGenerator.kt` and `ui/emergency/RegionalPanel.kt`. Existing Android package identities and v1 exchange schemas are retained. Room stores observation coordinates locally. The additive v3→v4 migration retains a nullable provider for new observations. Existing rows retain their coordinates, note, accuracy and timestamp. An unknown provider stays unknown. Manual input has no device measurement time or GPS accuracy.

## Earth and ChatGPT

Google publishes an association for `com.google.earth` on the Earth domain. The native path uses a package-targeted ACTION_VIEW with `https://earth.google.com/web/search/<latitude>,<longitude>/`. The coordinates retain round-trip decimal precision. Android intent dispatch alone cannot establish where Earth placed its camera. [Google Earth package](https://play.google.com/store/apps/details?id=com.google.earth) · [Google domain association](https://earth.google.com/.well-known/assetlinks.json) · [Google coordinate search](https://support.google.com/earth/answer/148068)

On the exact published beta7 APK, a synthetic saved observation at 44.43012345, 26.101234 reproduced the reported failure. The WebView browser received a decoded query of 500 characters ending in `Accur`. Its UTF-8 SHA-256 was `6af5141933f4c82984ea02c6a2d3ed4df0df87adb92719b84797a3941c9545e9`. Beta7 copied the full prompt to the clipboard, but applied Kotlin `take(500)` before URL encoding in the browser path. Its installed-app path opened the launcher without a text extra. The user's original receiver and input are unknown.

The replacement passes the complete reviewed text as `ACTION_SEND`, `text/plain`, `EXTRA_TEXT`, addressed to `com.openai.chatgpt`. It checks required fields, the completion marker, UTF-8 encodability and a 64 KiB application limit. Oversized text is rejected visibly. When sharing is unavailable the user can copy the reviewed text, open ChatGPT Web and paste. Receiver tests capture synthetic short and multilingual long payloads separately. Confirmation from an Android receiver establishes Android delivery. The third-party client's composer and model require separate observation.

## Geography, languages and emergency services

The wheel, EXE and APK package the same country grid and service catalog from `python/src/field_intelligence_console/regional_data/`. The grid generator verifies the input SHA-256 before accepting Natural Earth 5.1.1 Admin 0 boundaries at 1:50m. It includes whole interior cells with an approximately 5 km margin. Coastal areas, small countries, international borders and high latitudes may return unknown. Cartographic borders can differ from local emergency-service jurisdictions. [Pinned boundary source](https://github.com/nvkelso/natural-earth-vector/blob/9380cca83db5f9aef52d5e762765100745f84b27/geojson/ne_50m_admin_0_countries.geojson)

Android accepts a device fix for automatic country selection up to 120 seconds old with reported accuracy greater than zero and at most 1,000 m. A conflicting Geocoder result, future timestamp, saved or manual location requires confirmation. City and region are separately labelled geocoder data. A user-confirmed country persists until the user switches back to detected country. Windows offers a manual country confirmation on the selected place. Python provides `fic emergency --country RO` and `fic emergency --coordinates 44.43 26.10`.

Auto language takes the first supported system language, then the regional default, then English. Manual English, German or Romanian persists. Android 13+ uses the native per-app locale manager. Earlier supported Android versions use an app-only configuration context. These settings affect FIC only.

Catalog v1 contains national records with ISO code, category, geographical scope, verification status, date and official URL. Austria, Romania and Germany have country-specific entries. Other covered countries have the verified categories available in the file. An uncovered country or category displays an explicit unverified state. The UI uses ACTION_DIAL. [Austria](https://www.oesterreich.gv.at/en/themen/notfaelle_unfaelle_und_kriminalitaet/notrufnummern) · [Romania](https://civil-protection-humanitarian-aid.ec.europa.eu/what/civil-protection/national-disaster-management-system/romania_en) · [Germany](https://verwaltung.bund.de/leistungsverzeichnis/de/rechte-und-pflichten/102837939) · [EU emergency services](https://europa.eu/youreurope/citizens/travel/security-and-emergencies/emergency/)

## Verification history

First development checkpoint `8a5bfa519632c9d417ef8baf294177a665c2fdd7`. Python passed 49 tests and isolated wheel workflows. Android's first CI run failed lint because the original string resource file lacked German and Romanian translations. Windows initially refused the development-version suffix. At `1e7c560d4a6e81a01d6dad26578c73406fb03fd5`, a dedicated preflight accepted development package versions. Release preflight still rejects that suffix. The Windows EXE passed 14 focused, 29 packaged-widget and 7 native interaction checks. These results belong to their recorded source revisions. [Python workflow](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/45499283208) · [First Android run](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/45499283187) · [Windows workflow](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/45499666518)

Raw synthetic logs, payloads and build downloads live under ignored `.local/evidence/mission003/`. The project-owned emulator is `FIC_MISSION003_API34` on `emulator-5586`. Its named snapshot `mission003-beta7-synthetic` preserves the original installed beta7 and the reproduction record. Verify the serial and AVD name before using the device. A physical handset with Google Earth is required to finish native destination and return validation.
