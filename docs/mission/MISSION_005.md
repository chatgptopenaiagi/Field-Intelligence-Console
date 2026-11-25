# Mission 005 · Cognitive Experience Engine

## 1. Starea inițială

La 25 noiembrie 2026, ramura privată `v4-development` era curată la `a2999d4d683c5e1279116630c7f2279f5e7d443a`. Beta9 dev1 publicată provenea din `ccb3fe8c574c7ac1bd14bc026ecaf271bc4ce985`, cu Android code 12. GitHub confirma trei fluxuri CI reușite pentru acea sursă. Beta8 dev1 provenea din `1bdad1607736306278bfb49f0d39014935d30bd6`. Originea Manus AI și ideea unui singur buton cu funcții multiple sunt atribuite proprietarului. Mission 004 adăugase WORLD INTELLIGENCE ca a zecea acțiune a Matricei și integrase patru module din God's Eye View la revizia `1d92040d6eaa9dbdd2363608d27e228b4b8ce273`. [Istoricul beta9](MISSION_004.md) · [Proveniența integrării](../integrations/GODS_EYE_VIEW.md)

## 2. Probleme reproduse

Proprietarul a raportat pe Vivo că Matricea, harta, coordonatele, directorul și textele românești funcționau. A văzut GeoJSON la USGS, TLE la CelesTrak și o pagină albă la Open-Meteo. Randarea 3D pe acel telefon rămânea neconfirmată.

În beta9 directorul trimitea fiecare nume de furnizor direct la URL-ul din catalog. În proba HTTP a misiunii, USGS a răspuns cu 51 de evenimente GeoJSON, iar CelesTrak cu trei linii de text pentru ISS. Adresa Open-Meteo din director, fără parametri, a răspuns HTTP 200, cu tip JSON și **0 octeți**. Cu punctul sintetic 0°, 0°, variabila de temperatură și două zile solicitate, răspunsul a conținut 48 de ore în 1.413 octeți. Rezultatul explică pagina goală în mediul de probă. Tranzacția originală de pe Vivo nu a fost înregistrată. [Parametrii oficiali Open-Meteo](https://open-meteo.com/en/docs)

## 3. Îmbunătățiri realizate

În fluxul SCAN → OPEN MATRIX → WORLD INTELLIGENCE, un selector de subiect controlează două acțiuni. **Explorează** citește o singură sursă, iar **Sursă originală** deschide documentul corespunzător selecției. Actualizarea, exportul și partajarea sunt explicite. Fișa selectată leagă marcatorul de date, unități, distanță, dată și proveniență. Rezultatele includ descriere, acoperire și limitări în formatul v1 existent.

EONET afișează ultimul punct datat și categoria incendiului din catalog. Regiunea lipsă apare ca necunoscută. Arhiva AF304 conține 20 de observații din 2023. Expediția PS119 conține cele două extremități publicate în 2019. Citi Bike, Monterey și directoarele regionale oferă acces la regiunea acoperită. Pentru NWS, cererea la punctul selectat verifică acoperirea. Un eșec al furnizorului și o căutare validă fără rezultate au mesaje diferite.

Utilizatorul poate revizui și edita textul unei singure fișe. Android încearcă aplicația oficială ChatGPT și oferă selectorul de partajare dacă aceasta lipsește. Pe Windows, utilizatorul poate copia textul în aplicația AI aleasă. [Motorul geografic](../architecture/COGNITIVE_EARTH_ENGINE.md) · [Formatul de schimb](../architecture/COGNITIVE_KEYPOINTS.md)

## 4. Motorul USGS

Fișa afișează magnitudinea, locul comunicat, ora UTC, adâncimea disponibilă, epicentrul, distanța aproximativă și referința USGS. Datele sintetice au verificat ordinea longitudine–latitudine, adâncimea necunoscută și filtrele de magnitudine, timp și distanță. Pe WebView, cererea de probă a întors 51 de fișe. Magnitudinea descrie evenimentul. Aplicația nu calculează pericolul local.

## 5. Motorul orbital

ISS și Hubble folosesc SGP4. Fișa afișează identificatorul NORAD, epoca TLE, momentul calculului, altitudinea și poziția estimată. Traseul are 31 de puncte în 90 de minute. Fereastra de 24 de ore folosește eșantioane la un minut și un prag de elevație de 10°. În proba APK, ISS a avut cinci treceri, iar Hubble zero pentru observatorul sintetic. Prima trecere este afișată în UTC și în fusul orar al dispozitivului, cu elevația maximă eșantionată. Un TLE sintetic expirat a produs mesajul corespunzător și a blocat exportul unei predicții. Vizibilitatea optică depinde și de lumină, nori și relief.

## 6. Motorul meteorologic

Graficul orar afișează temperatura la doi metri în °C, ora de valabilitate și celula modelului. Linkul sursă conține aceiași parametri ca adaptorul. În proba APK au fost afișate 24 de valori viitoare. Testele sintetice au acoperit zero, valori negative, valori lipsă, unități greșite și întreruperea cererii. Eroarea de rețea elimină graficul anterior și lasă un mesaj în limba interfeței.

## 7. Cognitive Earth Visualization

Pe WebView 113 din Android 14, testul a citit pixeli din framebufferul WebGL, a rotit globul prin evenimente tactile și a regăsit imaginea inițială după recentrare. Pierderea controlată a contextului grafic a activat harta 2D, cu explicație. Restaurarea a permis revenirea la 3D. În Edge cu WebGL dezactivat, verificarea a selectat prin marcator a doua dintre două fișe sintetice. O cerere întârziată pentru un subiect părăsit nu a înlocuit rezultatele subiectului nou.

Testele intermediare au descoperit două defecte. Primul cadru putea selecta prematur 2D înainte de inițializarea texturii. Separat, terminarea procesului WebView putea închide aplicația. Corecțiile au fost verificate pe candidat. Un test Android termină procesul de randare prin API-ul platformei, apasă pe opțiunea de redeschidere și verifică noul document și rendererul 2D. Rezultatele aflate doar în memoria rendererului trebuie consultate din nou. [Contractul Android pentru terminarea rendererului](https://developer.android.com/reference/android/webkit/WebViewClient#onRenderProcessGone(android.webkit.WebView,android.webkit.RenderProcessGoneDetail))

## 8. Android și performanță

Pachetul de test este `com.fieldconsole.debug`, version code 13, Android 10+ și target API 34. Certificatul SHA-256 este `d8c8505ace1420fa6bb6e5022efbc37175a691c3d818d5d714a88ed2ca7c8731`. APK-ul instalat a fost recitit și comparat cu cel destinat distribuției. Nouă teste instrumentate au trecut pe emulatorul API 34 x86_64. Fluxul SCAN a expus toate cele zece acțiuni ale Matricei. Înapoi, Acasă, revenirea din browser, revenirea din fundal și repornirea la rece au fost exercitate cu date sintetice.

| Măsurătoare pe candidat | Rezultat pentru această rulare |
| --- | --- |
| Atingerea WORLD INTELLIGENCE până la confirmarea globului prin instrumentarea WebView | 750 ms, inclusiv verificarea externă a rendererului |
| DOMContentLoaded al documentului WebView | 350 ms |
| Prima oportunitate de redesenare măsurată după recentrare | 7 ms |
| Memorie proporțională PSS | 137.971 KiB procesul FIC și 52.563 KiB rendererul WebView asociat |
| Cadre Android raportate de gfxinfo în sesiunea aplicației | 149 din 628 cu întârzieri (23,73%). P50 26 ms, P95 97 ms |
| Interogare până la actualizarea fișei | USGS 792 ms, Open-Meteo 837 ms, ISS 5.993 ms, EONET 1.066 ms, aviație locală 6 ms, maritim local 5 ms |

Timpii de interogare includ răspunsul furnizorului și prelucrarea în client. Contoarele gfxinfo acoperă întreaga sesiune a procesului. Costul energetic, memoria GPU dedicată și comportamentul termic pe Vivo rămân nemăsurate.

CI a executat 102 teste unitare pentru fiecare variantă Debug și Release, cu zero eșecuri și zero omisiuni. Lint a raportat zero erori și șapte, respectiv șase avertismente. [Android CI](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/45572409687)

## 9. Windows

EXE-ul x64 portabil este nesemnat. Fereastra Place deschide clientul FIC împachetat în browserul implicit, cu coordonatele alese. Executabilul real a trecut 30 de verificări GUI, iar CI a executat și 14 teste focalizate și șapte verificări native. Edge a rulat clientul extras din acel EXE. La 320 px s-au verificat cardurile, filtrele, graficul, separarea sursei printr-o fereastră nouă și lipsa derulării orizontale. [Windows CI](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/45572409700)

## 10. Python

Wheel-ul `4.0.0b10.dev1` a fost instalat într-un mediu nou. USGS, prognoza și efemeridele ISS au fost interogate fără interfață grafică. Adaptoarele extinse folosesc Node 22+. CI pe Windows și Ubuntu a trecut. Recepția Windows înregistrează 53 de teste și șase verificări ale instalării izolate. Cele cinci teste deterministe ale motorului JS au trecut separat. [Python CI](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/45572409715)

## 11. Integritatea datelor

Compararea rândurilor din baza sintetică beta9 cu cele ale candidatului a regăsit exact toate cele 13 observații inițiale, două contacte și un raport. Scanările din misiune au adăugat observații. Verificările de actualizare și repornire au păstrat limba manuală și setarea regională. Testele au folosit emulatorul dedicat și instalarea în loc. Verificarea anterioară beta8 → beta9 rămâne consemnată în Mission 004.

Prin selectorul Android au fost exportate 20 de puncte AF304. Wheel-ul nou și cititorul beta9 au selectat câte un punct la o distanță de maximum 1 km. Wheel-ul nou a citit și exportul beta9. Wheel-ul publicat beta8 a validat documentele de contacte și de raport returnate de noul EXE. Toate cele 12 resurse ale clientului Earth, inclusiv licențele, au avut octeți identici în APK, EXE și WHL.

## 12. GitHub și instalare

Sursa produsului este `43175c2fd97988c6095a1bff10060a860c34da13`. Commitul `4ab7e0a` a introdus explorarea și modelul extins, `f54ff94` a corectat pornirea globului și stările furnizorilor, iar `43175c2` a adăugat recuperarea nativă. Numărul beta10 dev1 urmează convenția de identificare a versiunii testabile și a următorului cod Android. Prerelease-ul este destinat verificării pe hardware fizic. [Distribuție de test și instrucțiuni de instalare](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.10.dev1) · [Sume, surse și rezultate](evidence/mission-005/verification.json)

Distribuția a fost publicată la 08:14:58 UTC pe 25 noiembrie 2026. Compararea inventarelor GitHub înainte și după publicare a confirmat păstrarea numelor, dimensiunilor și sumelor celor 11 fișiere beta7, patru beta8 și patru beta9.

- **Android**. Autentifică-te în repository-ul privat și descarcă APK-ul și `SHA256SUMS.txt`. Verifică SHA-256. Pe Android 10+ deschide APK-ul și selectează actualizarea pentru beta8 dev1 sau beta9 dev1 cu același certificat. Păstrează beta7 pe dispozitivul său deoarece are un alt certificat. Dacă Android refuză actualizarea, păstrează instalarea și baza locală.
- **Windows**. Verifică suma EXE-ului x64, pornește-l, selectează coordonatele în Place și deschide World Intelligence. Menține fereastra stației de lucru deschisă în timpul utilizării clientului.
- **Python**. Într-un mediu Python 3.11+ separat, execută `python -m pip install field_intelligence_console-4.0.0b10.dev1-py3-none-any.whl`, apoi `fic earth status`. Pentru un export, folosește `fic earth export --input fisier.json --coordinates 21.7 -85.75 --radius-km 1 --output rezultat.json`.

## 13. Raport de testare

| Test de acceptanță | Stare și dovadă |
| --- | --- |
| 1. Matrice | PASS pe emulator, SCAN → zece acțiuni → WORLD INTELLIGENCE → Înapoi și Acasă |
| 2. USGS | PASS pentru parser, coordonate, adâncime lipsă, filtre, card, selectarea marcatorului și link original |
| 3. Sateliți | PASS pentru ISS/Hubble, traseu, treceri geometrice și refuzul TLE expirat |
| 4. Meteo | PASS pentru temperaturi orare, °C, grafic, zero, valori negative și eroare de rețea |
| 5. Hartă | PASS pe emulator și Edge, inclusiv rotație, centrare, context pierdut și renderer terminat. BLOCKED pe hardware Vivo |
| 6. Offline | PASS pentru mesajul meteo, exportul indisponibil după eșec și arhiva accesibilă |
| 7. Compatibilitate | PASS pentru date sintetice, preferințe și schimbul v1. BLOCKED pentru actualizarea utilizatorului pe telefonul fizic |
| 8. Interfață | PASS pentru interacțiunile exercitate și lățimea de 320 px. Verificarea pe alte dimensiuni fizice rămâne deschisă |
| 9. Date originale | PASS pe APK pentru destinațiile USGS, CelesTrak și Open-Meteo cu parametri, cardul intern și revenirea din browser la subiectul ales |
| 10. Distribuții | PASS pentru APK, EXE, WHL și manifestul SHA-256 descărcate din prerelease-ul publicat. Toate cele patru fișiere corespund octet cu octet inventarului local |

## 14. Limitări cunoscute

Testele pe Vivo, pe un GPU modest fizic și cu aplicațiile oficiale Google Earth și ChatGPT instalate rămân deschise. Emulatorul a folosit browserul alternativ pentru Earth și selectorul Android pentru textul revizuit. Disponibilitatea unui catalog sau a unui link nu confirmă activitatea unui incendiu, vizibilitatea unui satelit sau poziția actuală a unei nave. Textele originale și numele proprii ale furnizorilor pot rămâne în limba sursei. Interfața afișează maximum 80 de rezultate. Open-Meteo și NWS primesc coordonatele la consultarea explicită.

## 15. Next Codex Recovery Point

1. Citește această pagină și `docs/mission/evidence/mission-005/verification.json`. Verifică `git status -sb`, `git ls-remote origin refs/heads/v4-development refs/tags/v4.0.0-beta.10.dev1` și `gh release view v4.0.0-beta.10.dev1`. Pentru o etichetă adnotată, `git rev-parse 'v4.0.0-beta.10.dev1^{commit}'` trebuie să dea commitul produsului.
2. Recuperează fișierele publicate într-un director gol cu `gh release download v4.0.0-beta.10.dev1 --dir .local/evidence/mission005/handset-download`. Compară cele trei pachete cu fișierul SHA-256. Dovezile locale ale candidatului sunt în `.local/evidence/mission005/candidate/`. Etapele intermediare sunt în directoarele părinte și `final/`. Datele și pachetele istorice rămân disponibile separat.
3. Pe Vivo actualizează în loc versiunea compatibilă. Cu o observație sintetică, verifică limba, păstrarea datelor, SCAN, Matrice, rotația tactilă, recentrarea, modul 2D, graficul și un export. Repetă o cerere offline. Revino după suspendare și repornire. Notează versiunea WebView și timpii. Verifică destinația în Google Earth și textul în compozitorul oficial ChatGPT doar după revizuire.
4. Publică numai rezultate sintetice și agregate. Păstrează testele cu aceiași octeți deja verificate, tagurile istorice și vizibilitatea privată. Un nou defect de produs necesită un candidat nou și dovezi legate de artefactele sale.
