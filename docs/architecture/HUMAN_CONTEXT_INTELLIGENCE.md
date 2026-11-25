# Human Context Intelligence

The original Manus AI concept gives a single scan access to the Cognitive Matrix. AI ANALYSIS now opens one local editor. A free question and the prepared questions use the same preview. The Android Matrix keeps its other functions and its emergency exit.

## Offline catalog

The canonical [UTF-8 catalog](../../python/src/field_intelligence_console/human_context_data/cognitive_questions.json) ships as an Android asset, wheel resource and embedded Windows resource. Stable IDs and category keys are independent of the selected language. Each entry contains its title, question, RO/DE/EN translations, expected context, sensitivity, source expectations and schema version. Category rules, form labels and provenance terms share this resource.

| Category | Questions in ID order |
|---|---|
| Survival | Q01 shelters · Q02 evacuation · Q03 drinking water · Q04 extreme temperatures · Q05 requesting help in isolation |
| Emergency and medical | Q06 emergency services · Q07 emergency facilities · Q08 access for responders · Q09 fire risks · Q10 first aid |
| Public safety | Q11 police stations · Q12 site safety inspection · Q13 incident reporting · Q14 pedestrian safety on a patrol route · Q15 checking risks |
| Taxi and mobility | Q16 taxi stands · Q17 passenger pickup · Q18 traffic restrictions · Q19 parking, fuel and charging · Q20 trip fare |
| Finance and logistics | Q21 banks and ATMs · Q22 fees and services · Q23 loading areas · Q24 delivery planning · Q25 transport costs |
| Essential services | Q26 pharmacies · Q27 public facilities · Q28 accessibility · Q29 social support · Q30 multilingual communication |
| Earth and environment | Q31 forecast · Q32 earthquakes · Q33 environmental events · Q34 estimated ISS passes · Q35 geographic knowledge |

## Location and disclosure

Opening the editor reads local data. The default draft omits location. Region mode includes the available administrative region and the two country fields. Exact mode additionally includes the selected point, geocoded label, reported accuracy and measurement time where their provenance permits. Manual input has no device accuracy or device measurement time. History retains its historical designation. The detected country applies to the selected point. Manual country and a conflicting estimate are both shown. A conflict suppresses the country-specific responder term. Q30 accepts a target language in the editor. If none is specified, the prompt requests confirmation.

Android follows the app language. Windows stores the editor language in the workstation preferences. Notes and provider excerpts start empty unless the user expressly selects one result. The Earth panel can prepare a single record containing provider, dates, measurements and limitations. On Windows, copy this excerpt from the Earth panel and paste it in the editor. Free text, including pasted content, is visible in the full preview and may itself identify a place. Rebuilding the preview from the form applies the new disclosure settings to generated fields.

## Handoff

The preview is editable. Android copies the reviewed Unicode string or directs a plain-text share intent to the official ChatGPT package. If that destination is unavailable, the same preview stays available to copy. Windows opens the ChatGPT homepage separately from copying. Opening either destination does not establish receipt of text. An empty or oversized message is rejected for clipboard/Android handoff. The limit is 65,536 UTF-8 bytes. The application has no new AI service, API key, network request, background worker or database for questions.

## Python

```python
from field_intelligence_console import human_context as hc

groups = hc.categories()
mobility = hc.questions("mobility")
taxi = hc.question("Q16")
# Explicit synthetic manual point for this example.
draft = hc.compose("Q16", "ro", {"origin": "manual", "latitude": 0.0,
                   "longitude": 0.0}, disclosure="exact")
assert hc.reviewable(draft)
```

The accepted origins are `device`, `manual`, `historical` and `unknown`. Use `manual_origin=True` for a historical manual point. A measured time requires a timezone. Pass `custom`, `observation`, `earth_excerpt` and `target_language` explicitly where needed. `hc.earth_excerpt(document, record_id)` projects one selected record from an existing Earth Engine export. Composition returns text and performs no handoff. Android implements the same composition order with the Kotlin editor's local location object. See the [mission verification](../mission/MISSION_006.md) for package and device evidence.
