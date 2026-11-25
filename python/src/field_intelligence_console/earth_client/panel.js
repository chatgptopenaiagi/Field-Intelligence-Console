const $=id=>document.getElementById(id);
const params=new URLSearchParams(location.hash.slice(1));
const lang=['de','ro'].includes(params.get('lang'))?params.get('lang'):'en';
document.documentElement.lang=lang;
const ix={en:0,de:1,ro:2}[lang], tr=(...strings)=>strings[ix];
const nativeHost=location.hostname==='fic-earth.invalid';
const labels={
 title:['World Intelligence','Weltinformationen','Informații globale'],back:['Back','Zurück','Înapoi'],
 home:['Home','Start','Acasă'],change:['Change location','Ort ändern','Schimbă locul'],
 apply:['Use location','Ort verwenden','Folosește locul'],latLabel:['Latitude','Breitengrad','Latitudine'],
 lonLabel:['Longitude','Längengrad','Longitudine'],queryLabel:['Choose a geographical question','Geografische Frage auswählen','Alege o întrebare geografică'],
 refresh:['Show / refresh','Anzeigen / aktualisieren','Arată / actualizează'],save:['Export JSON','JSON exportieren','Exportă JSON'],
 rangeLabel:['Radius (km)','Umkreis (km)','Rază (km)']
};
for(const [key,words] of Object.entries(labels))$(key).textContent=tr(...words);
const questions={
 sources:['Which sources are available?','Welche Quellen sind verfügbar?','Ce surse sunt disponibile?'],
 earthquakes:['Where were earthquakes reported?','Wo wurden Erdbeben gemeldet?','Unde s-au raportat cutremure?'],
 aviation:['Where did the Idalia weather flight fly in 2023?','Wo flog der Idalia-Wetterflug 2023?','Unde a zburat misiunea meteo Idalia în 2023?'],
 satellites:['Which satellite passes are predicted here?','Welche Satellitenüberflüge werden hier erwartet?','Ce treceri de sateliți sunt estimate aici?'],
 weather:['What temperatures are forecast here?','Welche Temperaturen werden hier erwartet?','Ce temperaturi sunt prognozate aici?'],
 wildfires:['Where have wildfires been reported?','Wo wurden Waldbrände gemeldet?','Unde s-au raportat incendii?'],
 maritime:['Where did Polarstern sail in 2019?','Wo fuhr Polarstern 2019?','Unde a navigat Polarstern în 2019?'],
 infrastructure:['Where is the Monterey ocean buoy?','Wo liegt die Monterey-Messboje?','Unde se află baliza Monterey?'],
 transport:['Which NYC bike stations are mapped?','Welche NYC-Radstationen sind erfasst?','Ce stații de biciclete sunt în NYC?'],
 warnings:['Which US weather alerts apply here?','Welche US-Wetterwarnungen gelten hier?','Ce alerte meteo SUA sunt valabile aici?'],
 cameras:['Where are Yellowstone’s scenic webcams?','Wo sind die Yellowstone-Webcams?','Unde sunt camerele panoramice Yellowstone?'],
 routing:['Which NYC trip planner covers this region?','Welcher NYC-Routenplaner gilt hier?','Ce planificator de rute acoperă NYC?']
};
const notes={
 weather:['Refresh sends these coordinates to Open-Meteo. Hourly model forecast.','Aktualisieren sendet diese Koordinaten an Open-Meteo. Stündliche Modellprognose.','Actualizarea trimite coordonatele la Open-Meteo. Prognoză orară.'],
 warnings:['Refresh sends this point to the US National Weather Service. US coverage only.','Aktualisieren sendet diesen Punkt an den US-Wetterdienst. Nur USA.','Actualizarea trimite punctul la serviciul meteo SUA. Numai SUA.'],
 satellites:['SGP4 · 90-minute ground track and 24-hour geometric passes above 10°, sampled each minute. Daylight and clouds affect visibility.','SGP4 · 90 Minuten Bodenspur und 24 Stunden Überflüge über 10°, minütlich berechnet. Tageslicht und Wolken beeinflussen die Sicht.','SGP4 · traseu de 90 de minute și treceri peste 10° în 24 de ore, eșantionate la un minut. Vizibilitatea depinde de lumină și nori.'],
 aviation:['Archived onboard reports · 20 positions · August 2023.','Archivierte Bordmeldungen · 20 Positionen · August 2023.','Rapoarte de bord arhivate · 20 de poziții · august 2023.'],
 transport:['NYC Citi Bike station directory. Dock capacity only.','Standorte von NYC Citi Bike. Nur Dockkapazität.','Director Citi Bike NYC. Doar capacitatea stațiilor.'],
 maritime:['Two published endpoints of expedition PS119.','Zwei veröffentlichte Endpunkte der Expedition PS119.','Două puncte publicate ale expediției PS119.'],
 wildfires:['Up to 40 open EONET events with a reported point.','Bis zu 40 offene EONET-Ereignisse mit gemeldetem Punkt.','Până la 40 de evenimente EONET deschise cu punct raportat.'],
 earthquakes:['USGS · M2.5+ · previous day.','USGS · M2,5+ · letzter Tag.','USGS · M2,5+ · ultima zi.']
};
const topics=$('topic');
for(const [id,words] of Object.entries(questions)){topics.add(new Option(tr(...words),id));}
let origin=null, map=null, request=null,rows=[],visible=[],passes=[],loaded=false;
const idle=()=>tr('Choose a question, then refresh.','Frage wählen und aktualisieren.','Alege o întrebare și actualizează.');
const missing=()=>tr('Enter valid coordinates.','Gültige Koordinaten eingeben.','Introdu coordonate valide.');
function number(value){return value!==null&&String(value).trim()!==''?Number(value):NaN;}
function setPlace(lat,lon){
 const p={lat:number(lat),lon:number(lon)};if(!FicEarth.isGeoPoint(p)){$('status').textContent=missing();return false;}
 origin=p;$('lat').value=p.lat;$('lon').value=p.lon;$('location').textContent=tr('Selected location','Ausgewählter Ort','Loc selectat')+' · '+p.lat+', '+p.lon;
 if(map)map.setOrigin(p);else map=earthMap($('globe'),p);reset();return true;
}
function reset(){
 $('satChoice').hidden=topics.value!=='satellites';request?.abort();request=null;rows=[];passes=[];loaded=false;visible=[];$('passes').textContent='';$('results').replaceChildren();
 $('status').textContent=origin?idle():missing();$('save').disabled=true;$('refresh').disabled=!origin;
 $('scope').textContent=notes[topics.value]?tr(...notes[topics.value]):'';map?.show([]);
}
function link(label,href){
 const a=document.createElement('a');a.textContent=label;a.href=href;a.target=nativeHost?'_self':'_blank';a.rel='noopener noreferrer';return a;
}
function coverageLink(){
 if(topics.value==='routing'&&FicEarth.greatCircleKm(origin,{lat:40.73,lon:-73.99})<=100)return ['MTA · Trip Planner','https://www.mta.info/tripplanner'];
 if(topics.value==='cameras'&&FicEarth.greatCircleKm(origin,{lat:44.46,lon:-110.83})<=150)return ['National Park Service · Yellowstone','https://www.nps.gov/yell/learn/photosmultimedia/webcams.htm'];
 return null;
}
function listSources(){
 const list=$('results');list.replaceChildren();
 for(const [id,[provider,url]] of Object.entries(FicEarth.sources)){
   const li=document.createElement('li');li.append(link(provider,url),document.createTextNode(' · '+tr(...questions[id])));list.append(li);
 }
 $('status').textContent=tr('Source directory · availability is checked on refresh.','Quellenverzeichnis · Verfügbarkeit wird beim Abruf geprüft.','Director de surse · disponibilitatea se verifică la actualizare.');
}
function render(){
 const list=$('results');list.replaceChildren();
 const dist=r=>FicEarth.greatCircleKm(origin,{lat:r.latitude,lon:r.longitude});
 const radius=Number($('range').value);
 visible=rows.filter(r=>!radius||dist(r)<=radius);
 if(!['satellites','weather','aviation','maritime'].includes(topics.value)) visible.sort((a,b)=>dist(a)-dist(b));
 const shown=visible.slice(0,80);map?.show(shown);
 for(const r of shown){
   const li=document.createElement('li'),button=document.createElement('button');button.textContent=r.label;
   button.onclick=()=>map?.center({lat:r.latitude,lon:r.longitude});li.append(button);
   const values=Object.entries(r.measurements).filter(([,m])=>m).map(([k,m])=>k+' '+m.value+' '+m.unit);
   for(const line of [r.kind+' · '+(r.valid_at||r.observed_at||'UNKNOWN')+' · '+r.freshness,r.latitude+', '+r.longitude+' · '+Math.round(dist(r))+' km',values.join(' · '),tr('Retrieved','Abgerufen','Preluat')+' · '+r.retrieved_at]){
     if(line){const p=document.createElement('p');p.textContent=line;li.append(p);}
   }
   li.append(link(r.provider,r.source));list.append(li);
 }
 $('passes').textContent=topics.value==='satellites'?tr('Predicted pass samples (UTC)','Berechnete Überflüge (UTC)','Treceri estimate (UTC)')+'\n'+(passes.map(p=>p.first_sample+' → '+p.last_sample+' · '+p.max_elevation_deg.toFixed(1)+'°').join('\n')||'—'):'';
 $('status').textContent=shown.length+' / '+visible.length+' · '+tr('matching records','passende Datensätze','înregistrări corespunzătoare');
 if(!shown.length)$('status').textContent+=' · '+tr('Coverage may be incomplete.','Die Abdeckung kann unvollständig sein.','Acoperirea poate fi incompletă.');
 $('save').disabled=!loaded;
}
async function refresh(){
 reset();if(!origin)return;
 if(topics.value==='sources'){listSources();return;}
 if(['routing','cameras'].includes(topics.value)){
   const ref=coverageLink();$('status').textContent=ref?tr('Regional source','Regionale Quelle','Sursă regională'):'UNKNOWN';
   if(ref)$('results').append(link(...ref));return;
 }
 const controller=new AbortController();request=controller;
 const timer=setTimeout(()=>controller.abort(),18000);
 $('status').textContent=tr('Loading…','Wird geladen…','Se încarcă…');
 try{
   const result=await FicEarth.query(topics.value,origin,controller.signal,$('satellite').value);
   if(request!==controller)return;
   rows=result.records;passes=result.passes||[];loaded=true;render();
 }catch(error){if(request===controller)$('status').textContent=tr('Source unavailable','Quelle nicht verfügbar','Sursă indisponibilă')+' · UNKNOWN · '+error.message;}
 finally{clearTimeout(timer);if(request===controller)request=null;}
}
window.ficExport=()=>loaded?JSON.stringify({schema:'fic.earth-keypoints',version:1,selection:origin,category:topics.value,
 radius_km:Number($('range').value)||null,matching_count:visible.length,records:visible.slice(0,80),passes},null,2):'';
$('save').onclick=()=>{
 if(nativeHost){location.href='fic-local://export';return;}
 const url=URL.createObjectURL(new Blob([window.ficExport()],{type:'application/json'}));
 const a=document.createElement('a');a.href=url;a.download='fic-earth-keypoints.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
};
for(const id of ['back','home'])$(id).onclick=()=>{if(nativeHost)location.href='fic-local://'+id;else {window.close();$('status').textContent=tr('Return to the FIC window.','Zum FIC-Fenster zurückkehren.','Revino la fereastra FIC.');}};
$('earth').onclick=()=>{if(origin){const url='https://earth.google.com/web/search/'+origin.lat+','+origin.lon+'/';
 if(nativeHost)location.href=url;else window.open(url,'_blank','noopener,noreferrer');}};
$('apply').onclick=()=>setPlace($('lat').value,$('lon').value);
$('mode').textContent='2D';$('mode').onclick=()=>{if(map)$('mode').textContent=map.toggle()?'3D':'2D';};
$('center').ariaLabel=tr('Recenter','Zentrieren','Recentrează');$('center').onclick=()=>map?.center(origin);
$('refresh').onclick=refresh;topics.onchange=reset;$('satellite').onchange=reset;$('range').onchange=()=>{if(loaded)render();};
window.addEventListener('pagehide',()=>request?.abort());
reset();setPlace(params.get('lat'),params.get('lon'));
