const $ = id => document.getElementById(id);
const params = new URLSearchParams(location.hash.slice(1));
const nativeHost = location.hostname === 'fic-earth.invalid';
let preferred = params.get('lang');
if(!nativeHost && !preferred){
  try { preferred=localStorage.getItem('fic-earth-language'); } catch { /* Browser storage is optional. */ }
}
const lang = ['de','ro'].includes(preferred) ? preferred : 'en';
document.documentElement.lang = lang;
const tr = (en,de,ro) => ({en,de,ro})[lang];
const unknown = tr('Unknown','Unbekannt','Necunoscut');
const all = tr('All','Alle','Toate');
const original = tr('Original source','Originalquelle','Sursă originală');
const updated = tr('Retrieved','Abgerufen','Actualizat la');
const labels = {
  title:tr('World Intelligence','Weltinformationen','Informații globale'), back:tr('Back','Zurück','Înapoi'),
  home:tr('Home','Start','Acasă'),change:tr('Change location','Ort ändern','Schimbă locul'),
  apply:tr('Use location','Ort verwenden','Folosește locul'),latLabel:tr('Latitude','Breitengrad','Latitudine'),
  lonLabel:tr('Longitude','Längengrad','Longitudine'),queryLabel:tr('What would you like to explore?','Was möchten Sie erkunden?','Ce dorești să explorezi?'),
  save:tr('Export results','Ergebnisse exportieren','Exportă rezultatele'),rangeLabel:tr('Distance (km)','Entfernung (km)','Distanță (km)'),
  filters:tr('Filter results','Ergebnisse filtern','Filtrează rezultatele'),magLabel:tr('Minimum magnitude','Mindestmagnitude','Magnitudine minimă'),
  ageLabel:tr('Reported within','Gemeldet innerhalb','Raportat în ultimele'),satLabel:tr('Satellite','Satellit','Satelit'),
  chartTitle:tr('Hourly temperature · °C','Stündliche Temperatur · °C','Temperatură orară · °C'),
  analysisLabel:tr('Explain this result with AI','Dieses Ergebnis mit KI erklären','Explică acest rezultat cu AI'),
  review:tr('Review or edit the text to share','Text vor dem Teilen prüfen oder ändern','Verifică sau editează textul de transmis'),
  share:nativeHost?tr('Share reviewed text','Geprüften Text teilen','Partajează textul verificat'):tr('Copy reviewed text','Geprüften Text kopieren','Copiază textul verificat'),
  center:tr('Recenter','Zentrieren','Recentrează'),region:tr('Explore covered region','Abgedeckte Region erkunden','Explorează regiunea acoperită'),
  legend:tr('Turquoise ring · selected place. Amber · results. White · selected result.','Türkiser Ring · gewählter Ort. Gelb · Ergebnisse. Weiß · gewähltes Ergebnis.','Inel turcoaz · loc selectat. Galben · rezultate. Alb · rezultat selectat.'),
  fallback:tr('WebGL unavailable. The interactive 2D map is active.','WebGL nicht verfügbar. Die interaktive 2D-Karte ist aktiv.','WebGL indisponibil. Harta interactivă 2D este activă.')
};
for(const [id,value] of Object.entries(labels)) $(id).textContent=value;
$('globe').setAttribute('aria-label',tr('Interactive world map','Interaktive Weltkarte','Hartă mondială interactivă'));
$('range').options[0].text=$('age').options[0].text=all;
const topics={
 earthquakes:[tr('Earthquakes','Erdbeben','Cutremure'),tr('Reported epicentres worldwide, M2.5+ in the past day. Magnitude describes the event.','Weltweit gemeldete Epizentren ab M2,5 am letzten Tag. Die Magnitude beschreibt das Ereignis.','Epicentre raportate la nivel mondial în ultima zi, de la M2,5. Magnitudinea descrie evenimentul.')],
 satellites:[tr('Satellite passes','Satellitenüberflüge','Treceri de sateliți'),tr('Estimated 90-minute track and passes above 10° over the next 24 hours. One-minute samples. Clouds, light and terrain affect visibility.','Berechnete 90-Minuten-Bahn und Überflüge über 10° in den nächsten 24 Stunden, minütlich abgetastet. Wolken, Licht und Gelände beeinflussen die Sicht.','Traseu estimat pe 90 de minute și treceri peste 10° în următoarele 24 de ore, calculate la un minut. Vizibilitatea depinde de nori, lumină și relief.')],
 weather:[tr('Weather forecast','Wettervorhersage','Prognoză meteo'),tr('Sends the selected coordinates to Open-Meteo. Hourly model forecast for the nearest model grid cell.','Sendet die gewählten Koordinaten an Open-Meteo. Stündliche Modellprognose für die zugeordnete Rasterzelle.','Trimite coordonatele selectate la Open-Meteo. Prognoză orară pentru celula modelului asociată locului.')],
 wildfires:[tr('Reported wildfires','Gemeldete Waldbrände','Incendii raportate'),tr('Latest reported point for up to 40 open EONET events worldwide. Current fire activity is unconfirmed.','Letzter gemeldeter Punkt für bis zu 40 offene EONET-Ereignisse weltweit. Aktuelle Brandaktivität ist unbestätigt.','Ultimul punct raportat pentru cel mult 40 de evenimente EONET deschise la nivel mondial. Activitatea actuală a incendiului este neconfirmată.')],
 aviation:[tr('Aviation archive','Flugarchiv','Arhivă aviatică'),tr('Historical archive · 20 measurements from Idalia flight AF304 on 29 August 2023.','Historisches Archiv · 20 Messungen des Idalia-Fluges AF304 vom 29. August 2023.','Arhivă istorică · 20 de măsurători din zborul Idalia AF304 din 29 august 2023.')],
 maritime:[tr('Maritime archive','Schifffahrtsarchiv','Arhivă maritimă'),tr('Historical archive · departure and arrival of Polarstern expedition PS119, April–May 2019.','Historisches Archiv · Abfahrt und Ankunft der Expedition Polarstern PS119, April–Mai 2019.','Arhivă istorică · plecarea și sosirea expediției Polarstern PS119, aprilie–mai 2019.')],
 infrastructure:[tr('Monterey ocean buoy','Messboje Monterey','Baliza oceanografică Monterey'),tr('Published location of one station off Monterey, California. The buoy moves within its watch circle.','Veröffentlichter Standort einer Station vor Monterey, Kalifornien. Die Boje bewegt sich im Schwojkreis.','Poziție publicată pentru o singură stație de lângă Monterey, California. Baliza se deplasează în jurul punctului de ancorare.')],
 transport:[tr('New York bike stations','Fahrradstationen in New York','Stații de biciclete din New York'),tr('Citi Bike station inventory in the New York region. Capacity is the number of docks.','Citi-Bike-Stationsverzeichnis im Raum New York. Kapazität bezeichnet die Stellplätze.','Inventarul stațiilor Citi Bike din regiunea New York. Capacitatea indică numărul locurilor de andocare.')],
 warnings:[tr('US weather alerts','US-Wetterwarnungen','Alerte meteo SUA'),tr('Sends coordinates to the US National Weather Service to check point coverage and reported alerts.','Sendet Koordinaten an den US-Wetterdienst, um Abdeckung und Warnungen am gewählten Ort zu prüfen.','Trimite coordonatele la serviciul meteo SUA pentru verificarea acoperirii și a alertelor raportate.')],
 cameras:[tr('Yellowstone webcams','Yellowstone-Webcams','Camere Yellowstone'),tr('Public National Park Service webcam directory for Yellowstone. Camera status is unverified.','Öffentliches Webcambuch des National Park Service für Yellowstone. Kamerastatus ungeprüft.','Directorul public al camerelor National Park Service din Yellowstone. Starea camerelor este neverificată.')],
 routing:[tr('New York trip planner','Routenplaner New York','Planificator de rute New York'),tr('The MTA provides regional trip planning on its website.','Die MTA bietet regionale Routenplanung auf ihrer Website.','MTA oferă planificarea regională a rutelor pe site-ul său.')]
};
const regions={
 transport:{lat:40.73,lon:-73.99,radius:100},routing:{lat:40.73,lon:-73.99,radius:100},
 cameras:{lat:44.46,lon:-110.83,radius:150},infrastructure:{lat:36.787,lon:-122.408,radius:100},
 warnings:{lat:40.73,lon:-73.99}
};
const directories={
 cameras:['National Park Service','https://www.nps.gov/yell/learn/photosmultimedia/webcams.htm'],
 routing:['MTA','https://www.mta.info/tripplanner']
};
const kindLabels={REPORTED:tr('Reported','Gemeldet','Raportat'),HISTORICAL:tr('Historical archive','Historisches Archiv','Arhivă istorică'),PREDICTED:tr('Estimated','Berechnet','Estimat')};
const measurements={
 magnitude:tr('Magnitude','Magnitude','Magnitudine'),depth:tr('Depth','Tiefe','Adâncime'),altitude:tr('Altitude','Höhe','Altitudine'),
 temperature:tr('Temperature at 2 m','Temperatur in 2 m Höhe','Temperatură la 2 m'),
 geopotential_height:tr('Geopotential height','Geopotentielle Höhe','Înălțime geopotențială'),capacity:tr('Docks','Stellplätze','Locuri de andocare')
};
const topic=$('topic');
if(!nativeHost){
  $('languageBox').hidden=false;$('languageLabel').textContent=tr('Language','Sprache','Limba');
  $('language').value=lang;$('language').onchange=()=>{
    const value=$('language').value;
    try {localStorage.setItem('fic-earth-language',value);} catch { /* The fragment still works. */ }
    params.set('lang',value);
    if(origin){params.set('lat',origin.lat);params.set('lon',origin.lon);}
    location.hash=params.toString();
  };
}
for(const [key,[title]] of Object.entries(topics)) topic.add(new Option(title,key));
let origin=null,map=null,request=null,rows=[],visible=[],passes=[],selected=null,checked=null;
const dateFormat=new Intl.DateTimeFormat(lang,{year:'numeric',month:'short',day:'numeric',hour:'2-digit',minute:'2-digit',timeZone:'UTC',hour12:false});
const localFormat=new Intl.DateTimeFormat(lang,{month:'short',day:'numeric',hour:'2-digit',minute:'2-digit',timeZoneName:'short',hour12:false});
const fmt=n=>n.toLocaleString(lang,{maximumFractionDigits:2});
const time=v=>v && Number.isFinite(Date.parse(v))?(v.length===10?v:dateFormat.format(new Date(v))+' UTC'):unknown;
const distance=r=>FicEarth.greatCircleKm(origin,{lat:r.latitude,lon:r.longitude});
const activeSource=()=>directories[topic.value] || FicEarth.sources[topic.value];
function line(parent,value){
 const p=document.createElement('p');p.textContent=value;parent.append(p);
}
function reference(a,url,label){
 a.href=url;a.textContent=label;a.target=nativeHost?'_self':'_blank';a.rel='noopener noreferrer';
}
function regionMessage(){
 $('status').textContent=tr('This source does not cover the selected region.','Diese Quelle deckt die gewählte Region nicht ab.','Această sursă nu acoperă regiunea selectată.');
 $('region').hidden=false;
}
function setPlace(lat,lon,provenance='manual',stamp=null){
 const p={lat:Number(lat),lon:Number(lon)};
 if([lat,lon].some(x=>x==null||String(x).trim()==='')||!FicEarth.isGeoPoint(p)){
   $('status').textContent=tr('Invalid coordinates.','Ungültige Koordinaten.','Coordonate invalide.');return;
 }
 origin=p;$('lat').value=p.lat;$('lon').value=p.lon;
 const method={manual:tr('Manual selection','Manuelle Auswahl','Selectare manuală'),saved:tr('Saved observation','Gespeicherte Beobachtung','Observație salvată'),scan:'SCAN',matrix:tr('Selected location','Gewählter Ort','Loc selectat')}[provenance];
 $('location').textContent=[method||tr('Selected location','Gewählter Ort','Loc selectat'),p.lat+', '+p.lon,stamp?time(stamp):null].filter(Boolean).join(' · ');
 if(map)map.setOrigin(p);else map=earthMap($('globe'),p,r=>choose(r,true),(flat,fallback)=>{
   $('mode').textContent=flat?'3D':'2D';$('mode').disabled=fallback;$('fallback').hidden=!fallback;
 });
 reset();
}
function reset(){
 request?.abort();request=null;checked=null;rows=[];visible=[];passes=[];selected=null;
 for(const id of ['card','forecast','analysis','region','legend'])$(id).hidden=true;
 $('analysis').open=false;$('preview').value='';$('shareStatus').textContent='';$('card').replaceChildren();
 $('passes').textContent='';$('results').replaceChildren();map?.show([]);map?.select(null);
 $('magBox').hidden=$('ageBox').hidden=topic.value!=='earthquakes';
 $('satChoice').hidden=topic.value!=='satellites';
 $('filterBox').hidden=!!directories[topic.value];$('save').disabled=true;$('refresh').disabled=!origin;
 $('refresh').textContent=tr('Explore','Erkunden','Explorează');
 $('scope').textContent=activeSource()[0]+' · '+topics[topic.value][1];
 const url=directories[topic.value]?.[1] || (origin && FicEarth.originalUrl(topic.value,origin,$('satellite').value));
 $('original').hidden=!url;if(url)reference($('original'),url,original);
 $('status').textContent=tr('Ready to explore · availability unchecked','Bereit zum Erkunden · Verfügbarkeit ungeprüft','Pregătit pentru explorare · disponibilitate neverificată');
}
function recordTitle(r){
 switch(r.category){
   case 'weather':return fmt(r.measurements.temperature.value)+' °C · '+time(r.valid_at);
   case 'earthquakes':return 'M '+fmt(r.measurements.magnitude.value)+' · '+r.label;
   case 'aviation':return r.metadata.aircraft+' · '+r.metadata.mission+' · '+time(r.observed_at);
   case 'satellites':return r.label+' · '+time(r.valid_at);
   case 'maritime':return 'PS119 · Polarstern · '+time(r.observed_at);
   case 'infrastructure':return 'Monterey · 46042';
   default:return r.label;
 }
}
function choose(r,center=false){
 selected=r;map.select(r.id);
 if(center)map.center({lat:r.latitude,lon:r.longitude});
 for(const button of $('results').querySelectorAll('button'))button.setAttribute('aria-current',String(button.dataset.record===r.id));
 const card=$('card');card.replaceChildren();card.hidden=false;
 const title=document.createElement('h2');title.textContent=recordTitle(r);card.append(title);
 line(card,kindLabels[r.kind]+' · '+time(r.valid_at||r.observed_at));
 line(card,r.latitude.toFixed(4)+', '+r.longitude.toFixed(4)+' · ≈ '+fmt(Math.round(distance(r)))+' km');
 for(const [key,m] of Object.entries(r.measurements)){
   const unit=m && !['magnitude','docks'].includes(m.unit)?' '+m.unit:'';
   line(card,(measurements[key]||key)+' · '+(m?fmt(m.value)+unit:unknown));
 }
 if(r.category==='satellites'){
   line(card,'NORAD '+r.metadata.catalog_id+' · '+tr('Orbital epoch','Bahnepoche','Epoca elementelor orbitale')+' · '+time(r.metadata.element_epoch));
   line(card,tr('Calculated','Berechnet','Calculat la')+' · '+time(rows[0].valid_at));
 }
 if(r.category==='wildfires'){
   line(card,tr('Wildfire · open catalog entry','Waldbrand · offener Katalogeintrag','Incendiu · înregistrare deschisă în catalog'));
   line(card,tr('Reported region','Gemeldete Region','Regiune raportată')+' · '+(r.metadata.region||unknown));
 }
 line(card,updated+' · '+time(r.retrieved_at));
 const a=document.createElement('a');reference(a,r.source,r.provider+' · '+original);card.append(a);
 $('analysis').hidden=false;$('analysis').open=false;$('shareStatus').textContent='';
 $('preview').value=[tr('Explain this geographic result. Preserve unknowns, sources and times.','Erkläre dieses geografische Ergebnis. Kennzeichne Unbekanntes, Quellen und Zeitangaben.','Explică acest rezultat geografic. Păstrează necunoscutele, sursele și momentele.'),
  card.innerText,r.source,topics[r.category][1],r.category==='satellites'&&passes.length?$('location').textContent+'\n'+$('passes').textContent:''].filter(Boolean).join('\n');
}
function chart(data){
 const canvas=$('chart'),ctx=canvas.getContext('2d'),w=canvas.width,h=canvas.height;
 const temps=data.map(r=>r.measurements.temperature.value),lo=Math.floor(Math.min(...temps)),hi=Math.max(lo+1,Math.ceil(Math.max(...temps)));
 const first=Date.parse(data[0].valid_at),last=Date.parse(data.at(-1).valid_at);
 ctx.clearRect(0,0,w,h);ctx.font='17px system-ui';ctx.fillStyle='#dce7eb';
 const left=60,right=w-15,top=20,bottom=h-33;
 for(const [n,y] of [[hi,top],[lo,bottom]]){ctx.fillText(fmt(n)+'°',2,y+5);ctx.fillRect(left,y,right-left,1);}
 let prev=null;ctx.strokeStyle='#67dcca';ctx.fillStyle='#67dcca';ctx.lineWidth=3;
 for(const r of data){
   const t=Date.parse(r.valid_at),x=left+(right-left)*(t-first)/Math.max(1,last-first),y=bottom-(bottom-top)*(r.measurements.temperature.value-lo)/(hi-lo);
   if(prev && t-prev.t===3600000){ctx.beginPath();ctx.moveTo(prev.x,prev.y);ctx.lineTo(x,y);ctx.stroke();}
   ctx.beginPath();ctx.arc(x,y,3,0,2*Math.PI);ctx.fill();prev={x,y,t};
 }
 ctx.fillStyle='#dce7eb';ctx.fillText(data[0].valid_at.slice(11,16)+' UTC',left,h-6);
 ctx.textAlign='right';ctx.fillText(data.at(-1).valid_at.slice(11,16)+' UTC',right,h-6);ctx.textAlign='left';
 canvas.setAttribute('aria-label',labels.chartTitle+' · '+fmt(Math.min(...temps))+' – '+fmt(Math.max(...temps))+' °C');
}
function render(){
 const category=topic.value,radius=Number($('range').value),hours=Number($('age').value);
 visible=rows.filter(r=>(!radius||distance(r)<=radius) && (category!=='earthquakes'||
   (r.measurements.magnitude.value>=Number($('mag').value) && (!hours||(Date.parse(r.observed_at)>=Date.parse(checked)-hours*3600000 && Date.parse(r.observed_at)<=Date.parse(checked))))));
 if(!['satellites','weather','aviation','maritime'].includes(category))visible.sort((a,b)=>distance(a)-distance(b));
 const shown=visible.slice(0,80);map.show(shown,category==='satellites');$('legend').hidden=!shown.length;
 $('results').replaceChildren();
 for(const r of shown){
   const li=document.createElement('li'),button=document.createElement('button');
   button.textContent=recordTitle(r);button.dataset.record=r.id;button.onclick=()=>{choose(r,true);$('card').scrollIntoView({block:'center'});};
   li.append(button);$('results').append(li);
 }
 $('forecast').hidden=category!=='weather'||!shown.length;if(!$('forecast').hidden)chart(shown);
 if(category==='satellites'){
   const p=passes[0];$('passes').textContent=p?tr('Next estimated pass','Nächster berechneter Überflug','Următoarea trecere estimată')+' · '+time(p.first_sample)+' → '+time(p.last_sample)+'\n'+
   tr('Device time','Gerätezeit','Ora dispozitivului')+' · '+localFormat.format(new Date(p.first_sample))+' → '+localFormat.format(new Date(p.last_sample))+'\n'+
   tr('Peak sampled elevation','Maximale abgetastete Elevation','Elevație maximă eșantionată')+' · '+fmt(p.max_elevation_deg)+'°':
   tr('No qualifying pass in the sampled 24 hours.','Kein passender Überflug in den untersuchten 24 Stunden.','Nicio trecere corespunzătoare în cele 24 de ore calculate.');
 }
 $('status').textContent=shown.length?shown.length+' / '+visible.length+' · '+tr('results','Ergebnisse','rezultate'):
 tr('No results in the consulted data for these filters.','Keine Ergebnisse im abgefragten Datensatz für diese Filter.','Nu există rezultate în setul de date consultat pentru aceste filtre.');
 const availability=['aviation','maritime','infrastructure'].includes(category)
   ?tr('Local catalog','Lokaler Katalog','Catalog local'):tr('Source responded','Quelle erreichbar','Sursa a răspuns');
 $('status').textContent+=' · '+availability+' · '+time(checked);
 $('save').disabled=false;
 if(shown.length)choose(shown.find(r=>r.id===selected?.id)||shown[0]);else{
   selected=null;map.select(null);$('card').hidden=$('analysis').hidden=true;$('preview').value='';
 }
}
async function refresh(){
 reset();if(!origin)return;
 const category=topic.value,area=regions[category];
 if(area?.radius && FicEarth.greatCircleKm(origin,area)>area.radius){regionMessage();return;}
 if(directories[category]){
   map.center(area);$('status').textContent=tr('Regional directory · open the original source to consult it.','Regionales Verzeichnis · Originalquelle zur Abfrage öffnen.','Director regional · deschide sursa originală pentru consultare.');return;
 }
 const controller=new AbortController();request=controller;
 const timer=setTimeout(()=>controller.abort(),18000);
 $('status').textContent=tr('Loading…','Wird geladen…','Se încarcă…');
 try{
   const result=await FicEarth.query(category,origin,controller.signal,$('satellite').value);
   if(request!==controller)return;
   rows=result.records;passes=result.passes||[];checked=result.checked_at;render();
   $('refresh').textContent=tr('Refresh','Aktualisieren','Actualizează');
 }catch(error){
   if(request!==controller)return;
   console.warn('FIC Earth',category,error.name,error.message);
   if(error.message.includes('coverage'))regionMessage();
   else if(error.message.includes('seven-day'))$('status').textContent=tr('Expired orbital elements. Prediction unavailable.','Bahnelemente abgelaufen. Vorhersage nicht verfügbar.','Elemente orbitale expirate. Predicție indisponibilă.');
   else {
     const offline=!navigator.onLine?tr('No internet connection. ','Keine Internetverbindung. ','Fără conexiune internet. '):'';
     $('status').textContent=offline+(category==='weather'?tr('The weather source is temporarily unavailable.','Die Wetterquelle ist vorübergehend nicht verfügbar.','Sursa meteorologică este temporar indisponibilă.'):
     tr('Source temporarily unavailable.','Quelle vorübergehend nicht verfügbar.','Sursa este temporar indisponibilă.'));
   }
 }finally{clearTimeout(timer);if(request===controller)request=null;}
}
window.ficExport=()=>checked?JSON.stringify({schema:'fic.earth-keypoints',version:1,selection:origin,category:topic.value,
 checked_at:checked,radius_km:Number($('range').value)||null,matching_count:visible.length,records:visible.slice(0,80),passes},null,2):'';
window.ficReviewed=()=>!$('analysis').hidden && $('analysis').open?$('preview').value:'';
// Only the explicitly selected record. Request URLs and observer coordinates stay local.
window.ficQuestionExcerpt=()=>{
 if(!selected || !$('analysis').open || $('analysis').hidden)return '';
 const {provider,kind,observed_at,valid_at,retrieved_at,measurements,metadata}=selected;
 const epoch=metadata?.element_epoch;
 return JSON.stringify({provider,kind,observed_at,valid_at,retrieved_at,measurements,element_epoch:epoch},null,2);
};
$('questionExcerpt').textContent=nativeHost?'AI ANALYSIS':tr('Copy excerpt for AI Analysis','Auszug für KI-Analyse kopieren','Copiază extrasul pentru analiza AI');
$('questionExcerpt').onclick=async()=>{
 const excerpt=window.ficQuestionExcerpt();
 if(!excerpt)return;
 if(nativeHost){location.href='fic-local://question';return;}
 $('preview').value=excerpt;
 $('share').click();
};
$('share').onclick=async()=>{
 if(!window.ficReviewed().trim())return;
 if(nativeHost){location.href='fic-local://share';return;}
 try{await navigator.clipboard.writeText(window.ficReviewed());$('shareStatus').textContent=tr('Copied. Paste into your AI application.','Kopiert. In die KI-Anwendung einfügen.','Text copiat. Lipește-l în aplicația AI.');}
 catch{$('preview').select();$('shareStatus').textContent=tr('Copy the selected text.','Markierten Text kopieren.','Copiază textul selectat.');}
};
$('save').onclick=()=>{
 if(nativeHost){location.href='fic-local://export';return;}
 const url=URL.createObjectURL(new Blob([window.ficExport()],{type:'application/json'}));
 const a=document.createElement('a');a.href=url;a.download='fic-earth-keypoints.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
};
for(const id of ['back','home'])$(id).onclick=()=>{if(nativeHost)location.href='fic-local://'+id;else{window.close();$('status').textContent=tr('Return to the FIC window.','Zum FIC-Fenster zurückkehren.','Revino la fereastra FIC.');}};
$('earth').onclick=()=>{if(origin){const url='https://earth.google.com/web/search/'+origin.lat+','+origin.lon+'/';
 if(nativeHost)location.href=url;else window.open(url,'_blank','noopener,noreferrer');}};
$('apply').onclick=()=>setPlace($('lat').value,$('lon').value);
$('region').onclick=()=>{const area=regions[topic.value];setPlace(area.lat,area.lon);refresh();};
$('mode').onclick=()=>map?.toggle();$('center').onclick=()=>map?.center(origin);
$('refresh').onclick=refresh;topic.onchange=reset;$('satellite').onchange=reset;
for(const id of ['range','mag','age'])$(id).onchange=()=>{if(checked)render();};
window.addEventListener('pagehide',()=>request?.abort());
window.addEventListener('hashchange',()=>location.reload());
reset();setPlace(params.get('lat'),params.get('lon'),params.get('via')||'matrix',params.get('at'));
