# FIC Cognitive Earth Engine

Manus AI's original central SCAN entry still captures the selected place and opens the Cognitive Matrix. WORLD INTELLIGENCE is its tenth action. GOOGLE EARTH remains available from the original Matrix. Opening the new panel transfers latitude, longitude and the app language. Saved notes, contacts and the observation database stay in FIC.

| Entry | Execution and lifecycle |
| --- | --- |
| Android | Compose hosts one WebView. A private HTTPS origin serves a fixed list of packaged files. File access, content access and WebView geolocation are disabled. The app uses its existing location permission and selection. Back disposes the view and returns to the observation. Home returns to SCAN. Android's document picker handles a deliberate export. |
| Windows | The Place tab passes the selected coordinates to the packaged client in the default browser. The workstation stays open. The client reads local assets from the running EXE's extraction directory. Returning to FIC or closing the tab ends the interaction. Keep the workstation open while using or refreshing the tab. |
| Python | `fic earth status` lists the installed catalog. `fic earth aviation`, `earthquakes` and `export` run with Python alone. Other source adapters can run through the same bundled JavaScript with Node 22+ on PATH. Each invocation terminates after its query. |

The client renders a Cesium WGS84 ellipsoid in WebGL with packaged shaded relief. Drag rotates the globe. Selecting a record centers the camera, and the recenter control restores the selected place. A Canvas globe handles WebGL initialization failure. A 2D world map is available on demand. Rendering is event-driven, with a 560 × 260 backing surface and up to 80 visible markers. Terrain shading represents cartography. It supplies neither elevation samples nor live imagery.

The panel offers prewritten geographical questions in English, German and Romanian. A question selects a provider adapter. Only a deliberate refresh starts a request. Weather and US warnings disclose that they send the selected coordinates to their provider. Other remote feeds use public catalogs, with distance filtering on the client. HTTP, coverage and stale-orbit failures surface as UNKNOWN. Up to 4 MiB per feed and an 18-second timeout bound requests. An empty response describes the provider's coverage. Source labels and measurements are rendered as text. The user can open a source link, export results, or open Google Earth.

| Topic | Operational coverage |
| --- | --- |
| Earthquakes | USGS daily M2.5+ reported events, magnitude, depth and event time. |
| Aviation | Twenty archived NOAA/NHC observations of weather flight AF304 on 29 August 2023, including timestamp, position and geopotential height. Aircraft speed and heading are unknown in this extract. |
| Satellites | ISS and Hubble, fresh CelesTrak elements, predicted 90-minute ground tracks and 24-hour pass windows for a selected observer. A pass is sampled every minute above 10° elevation. Optical visibility also depends on weather, lighting and terrain. Elements more than seven days from the query time are rejected. |
| Environment | NASA EONET open wildfire point reports, Open-Meteo hourly forecasts and US NWS alerts for points with confirmed NWS coverage. |
| Maritime | Two archived 2019 Polarstern expedition endpoints. Live AIS is unavailable. |
| Infrastructure | A NOAA ocean observation buoy with source attribution. This inventory is limited to the named station. |
| Transport and routing | NYC Citi Bike station metadata within a 100 km coverage check and a regional link to MTA's planner. Timetables, live vehicle tracks and turn-by-turn routing are outside this build. |
| Cameras | Yellowstone's public scenic webcam directory is offered in the region. Camera position, uptime, visibility and video are unverified. |
| Spatial queries | Deterministic queries over returned records, local radius selection, source inspection and export. The build has no generative model or server-side action executor. |

```powershell
fic earth status
fic earth earthquakes --coordinates 48.21 16.37 --radius-km 500
fic earth aviation --coordinates 21.7 -85.75 --radius-km 1
fic earth satellites --satellite 20580 --coordinates 48.21 16.37
fic earth export --input fic-earth-keypoints.json --coordinates 21.7 -85.75 --radius-km 1 --output nearby.json
```

[Keypoint exchange](COGNITIVE_KEYPOINTS.md) · [Upstream record](../integrations/GODS_EYE_VIEW.md) · [Package verification](../mission/MISSION_004.md)
