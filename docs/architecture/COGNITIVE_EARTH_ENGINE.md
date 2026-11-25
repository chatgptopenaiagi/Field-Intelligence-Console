# FIC Cognitive Earth Engine

Manus AI's original central SCAN entry still captures the selected place and opens the Cognitive Matrix. WORLD INTELLIGENCE is its tenth action. GOOGLE EARTH remains available from the original Matrix. Opening the new panel transfers latitude, longitude and the app language. Saved notes, contacts and the observation database stay in FIC.

| Entry | Execution and lifecycle |
| --- | --- |
| Android | Compose hosts one WebView. A private HTTPS origin serves a fixed list of packaged files. File access, content access and WebView geolocation are disabled. The app uses its existing location permission and selection. Back disposes the view and returns to the observation. Home returns to SCAN. Android's document picker handles a deliberate export. |
| Windows | The Place tab passes the selected coordinates to the packaged client in the default browser. The workstation stays open. The client reads local assets from the running EXE's extraction directory. Returning to FIC or closing the tab ends the interaction. Keep the workstation open while using or refreshing the tab. |
| Python | `fic earth status` lists the installed catalog. `fic earth aviation`, `earthquakes` and `export` run with Python alone. Other source adapters can run through the same bundled JavaScript with Node 22+ on PATH. Each invocation terminates after its query. |

The client renders a Cesium WGS84 ellipsoid in WebGL with packaged shaded relief. Drag rotates the globe. Selecting a marker opens its card. Selecting a list entry centers the camera, and Recenter restores the selected place. Initialization failure or graphics context loss activates the interactive Canvas 2D map with a localized explanation. When WebGL recovers, the user can switch back to 3D. Rendering is event-driven, with a 560 × 260 backing surface and up to 80 visible markers.

One topic selector works in English, German and Romanian. Explore requests the chosen topic. Original source opens its provider document. Weather, warnings and orbital links use the same parameters as the adapter. Weather and US warnings disclose coordinate transmission. Successful queries show the time checked. Archive cards retain their earlier retrieval and observation times. The panel distinguishes unsupported regions, expired orbital elements, provider failures and empty filtered sets. An 18-second timeout and a 4 MiB limit bound each remote query. Changing topic or place cancels the previous query.

The seismic view filters magnitude, reported time and distance locally. The orbital view connects successive track samples and identifies the first geometric pass, its sampled elevation and times in UTC and the device time zone. The weather chart shows hourly model values in Celsius and leaves gaps where samples are missing. A result can be exported or reviewed and edited before a deliberate share on Android or clipboard copy on Windows. The reviewed text includes the selected record and its source. FIC can explore each topic independently of an AI application.

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
