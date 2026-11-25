# Mission 006 · Human Context Intelligence

## 1. Baseline

La 25 noiembrie 2026, beta10 dev1 era publicată în repository-ul privat. Produsul verificat provenea din `43175c2fd97988c6095a1bff10060a860c34da13`. Punctul de recuperare curat de pe `v4-development` era `1a3c659048d3807b3e803936cb4f5cd07368185e`. Misiunea 005 avea APK, EXE, WHL și trei fluxuri CI verificate. Verificările pe Vivo și în aplicațiile destinatare oficiale au rămas deschise. [Dovezile beta10](MISSION_005.md)

## 2. Implementare

Catalogul canonic se află în `python/src/field_intelligence_console/human_context_data/cognitive_questions.json`. Android îl citește din assets, Python din resursele pachetului și Windows din wheel-ul inclus în EXE. `HumanContext.kt` și `human_context.py` folosesc aceleași întrebări, reguli și etichete. `HumanAnalysis.kt` și `windows/human_analysis.py` conțin editoarele native. Implementarea păstrează conceptul Manus AI al proprietarului și cele zece acțiuni existente ale Matricei. Datele externe se consultă prin motoarele existente. [Arhitectură și exemple Python](../architecture/HUMAN_CONTEXT_INTELLIGENCE.md)

## 3. Catalog

35 de identificatori consecutivi Q01–Q35, șapte categorii cu câte cinci întrebări, 105 texte de întrebări și 105 titluri în RO/DE/EN. Catalogul, căutarea, compunerea și previzualizarea funcționează local. În probele cu același context sintetic Q08, compozitoarele Android și Python au produs texte identice octet cu octet în toate cele trei limbi.

## 4. AI ANALYSIS

Android păstrează fluxul SCAN → OPEN MATRIX → AI ANALYSIS. Utilizatorul poate scrie liber, căuta Q16 sau selecta categoria de mobilitate. După alegerea întrebării, poate edita textul, schimba nivelul de localizare, introduce observații și examina mesajul complet. Q30 acceptă limba dorită pentru comunicare. Pentru întrebările despre urgențe există acces direct la ecranul de urgență. Pe Windows, editorul se deschide din Selected place → AI Analysis.

## 5. Geolocalizare

Starea inițială este „Fără locație”. Opțiunea regională include regiunea și țările disponibile. Opțiunea exactă poate include coordonatele, precizia raportată, momentul măsurătorii și eticheta geocodificată. Modelul distinge punctul măsurat de dispozitiv, punctul manual, observația istorică și locația necunoscută. Coordonatele manuale nu dobândesc precizie GPS. Țara estimată și cea selectată se păstrează separat. Când diferă, editorul de urgență cere confirmarea jurisdicției. Pentru Q08, o jurisdicție confirmată în context determină termenul SMURD sau Rettungsdienst. În celelalte cazuri întrebarea folosește o denumire generică.

## 6. ChatGPT

Previzualizarea Android permite copierea și partajarea explicită de text simplu către `com.openai.chatgpt`. Testul cu un receptor Android sintetic a verificat egalitatea octeților pentru mesaje scurte și lungi cu diacritice, Unicode și caracterul &. Pe emulator a fost verificată alternativa de copiere când aplicația oficială lipsea, precum și păstrarea previzualizării după revenire. Windows deschide pagina principală ChatGPT. Utilizatorul lipește textul și verifică mesajul la destinație. Recepția în aplicația oficială rămâne neverificată.

## 7. Confidențialitate

Editorul începe fără notă, contacte, fotografii sau istoric atașat. Nota observației curente poate fi adăugată deliberat. Datele unui rezultat Earth selectat includ sursa, tipul, momentele disponibile și măsurători. Pe Android am verificat trecerea unei fișe istorice NOAA în editor și absența coordonatelor observatorului și a URL-ului de interogare din extras. Textul introdus sau lipit manual trebuie și el verificat în previzualizare. Copierea folosește clipboardul sistemului. Anularea închide fluxul.

## 8. Android

APK de test Android 10+, `com.fieldconsole.debug`, versionCode 14, certificat SHA-256 `d8c8505ace1420fa6bb6e5022efbc37175a691c3d818d5d714a88ed2ca7c8731`. Pe emulatorul API 34 au trecut 11 teste instrumentate, inclusiv migrare, rețea de contacte, recuperare WebView, interfața întrebărilor și transfer de text. SCAN și OPEN MATRIX au fost executate cu date sintetice. Cele zece acțiuni au fost regăsite. WORLD INTELLIGENCE și ecranul de urgență s-au deschis. Navigația și logistica au folosit browserul disponibil. Google Earth a prezentat alternativa pentru destinația absentă. [Rezultate Android CI](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/45579323614)

În CI Android au trecut 102 teste pentru Debug și 102 pentru Release. Lint a raportat zero erori, cu șapte avertismente în Debug și șase în Release. Pe emulator, selecția Q30 și previzualizarea au fost verificate și în română și germană. Limba sistemului s-a păstrat.

## 9. Windows

EXE x64 cu versiunea numerică 4.0.0.11. Au trecut 14 teste focalizate, 34 de verificări ale interfeței împachetate și șapte verificări native. Testul Tk a ales Q16, a editat și copiat un text Unicode lung și a verificat că acțiunea de browser primește exclusiv pagina principală ChatGPT. EXE-ul descărcat a trecut și opt verificări locale în modul self-test. Executabilul este nesemnat. [Rezultate Windows CI](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/45579323531)

## 10. Python

Versiunea PEP 440 este `4.0.0b11.dev1`. Wheel-ul a fost instalat într-un mediu local nou. Toate cele 56 de teste au trecut pe pachetul instalat. CI a verificat și șase operații de instalare și utilizare izolată. Catalogul și cele 12 resurse ale clientului Earth au fost identice în sursa Git, APK, EXE și WHL. O probă intermediară a identificat terminatoare de linie diferite în resursele vechi din distribuția Windows. Regulile Git fixează acum reprezentarea comună. [Rezultate Python CI](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/45579323635)

## 11. Performanță

| Măsurătoare | Beta10 | Beta11 | Diferență |
|---|---:|---:|---:|
| APK, octeți | 61.722.124 | 61.673.441 | −48.683 |
| EXE, octeți | 16.445.665 | 16.474.725 | +29.060 |
| WHL, octeți | 276.454 | 287.926 | +11.472 |
| Pornire Android, mediana a trei lansări la rece | 2.646 ms | 2.761 ms | +115 ms |
| AI ANALYSIS, atingere până la captura UI Automator | 2.628 ms | 2.717 ms | +89 ms |
| PSS Android, ecran inițial | 94.269 KiB | 94.245 KiB | −24 KiB |
| PSS Android, AI ANALYSIS | 102.642 KiB | 102.924 KiB | +282 KiB |
| Windows, lansare până la detectarea ferestrei | 2.902 ms | 2.413 ms | −489 ms |
| Windows, memoria rezidentă a procesului GUI | 50.749.440 B | 50.634.752 B | −114.688 B |

Măsurătorile Windows sunt mediane din trei lansări alternate ale executabilelor distribuite cu profiluri sintetice. PSS și deschiderea editorului Android sunt observații individuale pe emulator. Captura UI Automator include timpul mecanismului de observare. În wheel-ul beta11 pe Python 3.13, mediana citirii și decodării catalogului fără cache de aplicație a fost 0,492 ms în 100 de probe. Compunerea Q08 cu catalogul în memorie a avut mediana 0,0189 ms în 1.000 de probe. Beta10 nu avea catalog. Aceste probe nu stabilesc performanța telefonului Vivo.

## 12. Integritatea datelor

Pe o imagine API 34 rezervată testului am instalat APK-ul beta10 redistribuit, am inițializat un profil exclusiv sintetic, am deschis aplicația și am înregistrat amprentele fiecărui tabel. Am instalat beta11 direct cu `adb install -r`, apoi am repornit aplicația. Cele 16 observații, două contacte, un raport, cele două tabele goale și preferințele regionale au păstrat aceleași amprente. APK-ul extras după actualizare este identic cu pachetul de distribuție. [Dovezi sintetice agregate](evidence/mission-006/verification.json)

## 13. GitHub

Ramura este `v4-development`. Commitul inițial al editorului este `e67f67da1d50e168750288fbd26572242635d4a0`. Sursa produsului beta11 este `cc2df9c69c8dfe2595be642f5573bdc5d6034462`. Tagul de testare este `v4.0.0-beta.11.dev1`. [Distribuția privată de testare](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.11.dev1)

## 14. Limitări

**PASS** pentru catalog, compunere și proveniență, controale de partajare, copiere, regresiile locale executate, actualizarea sintetică și pachetele testate. **BLOCKED** pentru verificarea pe Vivo, citirea textului în compozitorul oficial ChatGPT și destinația în aplicația nativă Google Earth. Istoricul unui test de browser sau deschiderea unei aplicații nu stabilește livrarea unui mesaj. Condițiile reale, locurile libere, timpii de intervenție și disponibilitatea serviciilor necesită verificare din surse datate.

## 15. Recuperare și verificare pe Vivo

1. Citește acest document, arhitectura și dovada JSON. Rulează `git status -sb`, `git rev-parse 'v4.0.0-beta.11.dev1^{commit}'` și `gh release view v4.0.0-beta.11.dev1`. Commitul tagului trebuie să coincidă cu sursa produsului de la secțiunea 13. Handoff-ul de documentație poate fi ulterior.
2. Descarcă distribuția într-un director nou cu `gh release download v4.0.0-beta.11.dev1 --dir .local/evidence/mission006/vivo-download`. Compară APK, EXE și WHL cu SHA256SUMS.txt. Dovezile brute locale sunt în `.local/evidence/mission006/final/`.
3. Pe Vivo, confirmă numele pachetului și certificatul instalat. Actualizează în loc instalarea compatibilă. Dacă semnătura diferă, oprește instalarea și păstrează datele. Beta7 avea un alt certificat. Folosește o observație sintetică și consemnează numărul observațiilor, contactelor și rapoartelor înainte și după actualizare.
4. Deschide SCAN → OPEN MATRIX → AI ANALYSIS. Alege Taxi și mobilitate → Q16. Verifică previzualizarea fără locație, cu regiune și cu coordonate. Editează întrebarea, adaugă un termen cu diacritice și caracterul & și copiază mesajul. Verifică și Anulează.
5. Deschide ChatGPT numai după revizuire. Compară începutul, sfârșitul, diacriticele și coordonatele cu textul copiat. Dacă textul lipsește, lipește-l manual. Revino în FIC și verifică întrebarea, limba, preferințele și datele după repornire. Documentează doar rezultate sintetice și versiuni ale aplicațiilor.
