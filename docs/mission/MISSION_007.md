# Misiunea 007 · Lansare publică GitHub

La 25 noiembrie 2026, repository-ul existent a trecut de la **PRIVATE** la **PUBLIC**. Proprietarul a autorizat accesul liber la cod și distribuții. **Beta 11 Human Context Intelligence** este versiunea experimentală recomandată pentru testare pe Android, Windows și Python. [Repository](https://github.com/chatgptopenaiagi/Field-Intelligence-Console) · [Beta 11](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.11.dev1) · [Toate distribuțiile](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases)

## Starea inițială

Ramura locală și cea de pe GitHub coincideau la `8fdf17e300bfdcaadf4b7d10a1db285eac9d8899`. Tagul beta11 indica produsul `cc2df9c69c8dfe2595be642f5573bdc5d6034462`. Inventarul inițial conținea cinci prerelease-uri publicate în repository-ul privat și draftul beta1 cu 11 fișiere. About nu avea website sau subiecte. API-ul repository-ului răspundea cu HTTP 404 fără autentificare.

## Verificarea înainte de publicare

Au fost examinate cele 64 de commituri accesibile din ramură și taguri, 861 de obiecte de conținut distincte, metadatele Git și descrierile release-urilor. Verificarea a inclus cele 42 de fișiere de distribuție, 137 de artefacte Actions și 104 arhive de jurnal pentru 103 execuții. Șapte arhive de jurnal erau goale. Documentele cu exemple de contacte, rapoartele și coordonatele semnalate au fost examinate în contextul testelor sintetice, surselor geografice și atribuirilor publice. Nu au rămas constatări de secrete sau date personale care să împiedice publicarea. Verificarea prin modele de text și proveniență are acoperire limitată.

Captura din README provine din APK-ul beta11 executat pe emulator în Misiunea 006. A fost verificată vizual și pentru metadate text/EXIF. Arată Q30 în română, cu locația omisă.

## Prezentare și arhivă

README începe cu beta11 și linkuri permanente către APK, EXE și WHL. Descrierea GitHub menționează beta11, câmpul Website deschide pagina release-ului și sunt configurate zece subiecte. Beta11 păstrează statutul de prerelease. Titlul și descrierea ei au fost actualizate. Draftul beta1 a fost publicat ca prerelease istoric, cu fișierele originale.

| Release | Fișiere | Rol |
|---|---:|---|
| [v4.0.0-beta.11.dev1](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.11.dev1) | 8 | Versiunea publică recomandată pentru testare |
| [v4.0.0-beta.10.dev1](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.10.dev1) | 4 | Arhivă istorică |
| [v4.0.0-beta.9.dev1](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.9.dev1) | 4 | Arhivă istorică |
| [v4.0.0-beta.8.dev1](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.8.dev1) | 4 | Arhivă istorică |
| [v4.0.0-beta.7](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.7) | 11 | Arhivă istorică |
| [v4.0.0-beta.1](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.1) | 11 | Arhivă istorică · draftul existent a fost publicat |

Beta2, beta3, beta4, beta5 și beta6, precum și baza v3 recuperată, sunt disponibile ca taguri de sursă. Nu aveau distribuții în GitHub Releases la începutul misiunii. [Toate tagurile](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/tags)

## Acces anonim și integritate

**PASS.** Un client HTTPS fără token sau cookie-uri a verificat pagina repository-ului, lista Releases, toate cele șase pagini individuale, codul-sursă beta11, README-ul randat și captura. A urmărit redirecționările și a citit integral toate cele 42 de fișiere. Cele 42 de dimensiuni și amprente SHA-256 coincid cu inventarul privat anterior lansării. Toate intrările din cele șase manifeste SHA256SUMS au fost reconciliate cu fișierele originale. Cele 12 taguri și destinațiile lor au fost păstrate. [Inventarul fiecărui fișier, amprente și verificări HTTP](evidence/mission-007/public-launch.json)

| Pachet beta11 | Octeți | SHA-256 |
|---|---:|---|
| [FieldIntelligenceConsole-4.0.0-beta.11.dev1-android-test.apk](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/download/v4.0.0-beta.11.dev1/FieldIntelligenceConsole-4.0.0-beta.11.dev1-android-test.apk) | 61.673.441 | `e35e21590eacea54ebea45120e210b401ccd2b2c43dc918d9aee1c0041c0043d` |
| [FieldIntelligenceConsole-4.0.0-beta.11.dev1-windows-x64.exe](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/download/v4.0.0-beta.11.dev1/FieldIntelligenceConsole-4.0.0-beta.11.dev1-windows-x64.exe) | 16.474.725 | `063bab0ea85b8d5bd999baa5df4c8034db12dddbb35f6780b3968e2e97e74903` |
| [field_intelligence_console-4.0.0b11.dev1-py3-none-any.whl](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/download/v4.0.0-beta.11.dev1/field_intelligence_console-4.0.0b11.dev1-py3-none-any.whl) | 287.926 | `dfd0368521ee31a9141071d4bca6c1d6e4912724177aefdfb9959d311c1e9564` |

## Limite și recuperare

Beta11 rămâne experimentală. Testarea fizică pe Vivo și confirmarea primirii textului în aplicația oficială ChatGPT sunt în continuare deschise. Destinația nativă Google Earth rămâne neverificată, iar executabilul Windows este nesemnat. Această misiune a utilizat distribuțiile existente. [Rezultatele testelor produsului](MISSION_006.md)

Commitul prezentării publice este [`3d8ef6ec34fd19f1cb7988d9fdb6327a9aec47ea`](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/commit/3d8ef6ec34fd19f1cb7988d9fdb6327a9aec47ea). Commitul de închidere poate fi identificat în istoricul ramurii cu `git log -1 --format=%H --fixed-strings --grep="Record Mission 007 anonymous public launch verification" origin/v4-development`. Descrierea release-ului beta11 conține și legătura permanentă către raportul din acel commit.
