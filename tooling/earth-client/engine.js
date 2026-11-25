import {greatCircleKm, isGeoPoint} from './upstream/geoDistance.js';
import {normalizeEarthquakeSnapshot} from './upstream/records.js';
import {parseTleText} from './upstream/tle.js';
import {parseStationInformation} from './upstream/gbfsStations.js';
import {twoline2satrec, propagate, gstime, eciToGeodetic, eciToEcf, ecfToLookAngles} from 'satellite.js';
import Ellipsoid from '@cesium/engine/Source/Core/Ellipsoid.js';
import EllipsoidGeometry from '@cesium/engine/Source/Core/EllipsoidGeometry.js';
import Cartesian3 from '@cesium/engine/Source/Core/Cartesian3.js';
import VertexFormat from '@cesium/engine/Source/Core/VertexFormat.js';
import texture from './natural-earth.jpg';
import aviation from '../../python/src/field_intelligence_console/earth_client/aviation.json';

export {texture, isGeoPoint, greatCircleKm};
export const radiusKm = Ellipsoid.WGS84.maximumRadius / 1000;
export function globeMesh(){
  const axes=Ellipsoid.WGS84.radii, scale=1/axes.x;
  const mesh=EllipsoidGeometry.createGeometry(new EllipsoidGeometry({
    radii:Cartesian3.multiplyByScalar(axes,scale,new Cartesian3()),stackPartitions:32,slicePartitions:64,
    vertexFormat:VertexFormat.POSITION_ONLY
  }));
  return {vertices:new Float32Array(mesh.attributes.position.values),indices:new Uint16Array(mesh.indices)};
}
import catalog from '../../python/src/field_intelligence_console/earth_client/sources.json';
export const sources = Object.fromEntries(Object.entries(catalog.feeds).map(([id, v]) => [id, [v.provider, v.url]]));

function point(category,id,label,lat,lon,time,received,kind,measurements={},metadata={},source=sources[category][1]){
  if(!isGeoPoint({lat,lon})) throw Error('Invalid provider coordinates');
  return {id:category+':'+id,category,label,latitude:lat,longitude:lon,observed_at:kind==='PREDICTED'?null:time,
    valid_at:kind==='PREDICTED'?time:null,retrieved_at:received,provider:sources[category][0],source,
    kind,verification:kind==='PREDICTED'?'model-output':'provider-reported',measurements,metadata,
    freshness:kind==='PREDICTED'?'forecast':!time?'unknown':Date.parse(received)-Date.parse(time)>86400000?'older-than-24h':'within-24h'};
}
function utc(value){return value!=null && Number.isFinite(new Date(value).getTime())?new Date(value).toISOString():null;}
function measurement(value,unit){return Number.isFinite(value)?{value,unit}:null;}
export function quakes(data,received){
  const rows=normalizeEarthquakeSnapshot(data);
  if(!rows)throw Error('Invalid USGS snapshot');
  return rows.map(r=>point('earthquakes',r.stableId,r.place||r.stableId,r.lat,r.lon,utc(r.time),received,'REPORTED',
    {magnitude:measurement(r.mag,'magnitude'),depth:measurement(r.depthKm,'km')},{},r.usgsId?'https://earthquake.usgs.gov/earthquakes/eventpage/'+encodeURIComponent(r.usgsId):sources.earthquakes[1]));
}
export function orbit(text,origin,now,catalogId='25544'){
  const tle=parseTleText(text).find(r=>r.line1.slice(2,7)===catalogId);
  if(!tle)throw Error('Missing orbital elements');
  const sat=twoline2satrec(tle.line1,tle.line2);
  const epoch=(sat.jdsatepoch-2440587.5)*86400000;
  if(!Number.isFinite(epoch)||Math.abs(epoch-now)>7*86400000)throw Error('Elements outside the seven-day window');
  const rad=Math.PI/180;
  const observer={latitude:origin.lat*rad,longitude:origin.lon*rad,height:0};
  const track=[],passes=[];
  let active=null;
  for(let minute=0;minute<=1440;minute++){
    const date=new Date(now+minute*60000),state=propagate(sat,date);
    if(!state?.position)throw Error('SGP4 propagation failed');
    const gmst=gstime(date),loc=eciToGeodetic(state.position,gmst);
    const elevation=ecfToLookAngles(observer,eciToEcf(state.position,gmst)).elevation/rad;
    if(minute<=90 && minute%3===0) track.push(point('satellites',catalogId+':'+date.toISOString(),tle.name,loc.latitude/rad,loc.longitude/rad,date.toISOString(),new Date(now).toISOString(),'PREDICTED',{altitude:measurement(loc.height,'km')},{element_epoch:utc(epoch),catalog_id:catalogId},sources.satellites[1].replace('25544',catalogId)));
    if(elevation>=10){
      if(!active){active={first_sample:date.toISOString(),last_sample:date.toISOString(),max_elevation_deg:elevation};passes.push(active);}
      active.last_sample=date.toISOString();active.max_elevation_deg=Math.max(active.max_elevation_deg,elevation);
    }else active=null;
  }
  return {records:track,passes};
}
async function read(url,signal,asText=false){
  const response=await fetch(url,{signal,credentials:'omit',referrerPolicy:'no-referrer',cache:'no-store'});
  if(!response.ok)throw Error('HTTP '+response.status);
  const reader=response.body.getReader(),decoder=new TextDecoder();
  let bytes=0,text='';
  try{while(true){const {done,value}=await reader.read();if(done)break;
    bytes+=value.byteLength;if(bytes>4*1024*1024){await reader.cancel();throw Error('Feed exceeds 4 MiB');}
    text+=decoder.decode(value,{stream:true});}
  }finally{reader.releaseLock();}
  text+=decoder.decode();return asText?text:JSON.parse(text);
}
export async function query(category,origin,signal,catalogId='25544'){
  if(!isGeoPoint(origin))throw Error('Invalid selected location');
  const now=Date.now(),received=utc(now);
  const url=sources[category][1];
  switch(category){
    case 'aviation':return {records:aviation};
    case 'infrastructure':return {records:[point(category,'46042','46042 · Monterey ocean observation buoy',36.787,-122.408,null,'2026-11-25','REPORTED',{}, {scope:'Published station location; buoy moves within its watch circle.'})]};
    case 'maritime':return {records:[
      ['departure',-53.125323,-70.858409,'2019-04-13T12:00:00Z'],
      ['arrival',-51.666429,-57.768814,'2019-05-31T10:56:30Z']
    ].map(([id,lat,lon,time])=>({...point(category,'PS119:'+id,'Polarstern · PS119 · '+id,lat,lon,time,'2026-11-25','HISTORICAL',{}, {scope:'Published expedition endpoint'}),freshness:'historical'}))};
    case 'warnings':{
      const coords=origin.lat.toFixed(4)+','+origin.lon.toFixed(4);
      const zone=await read('https://api.weather.gov/points/'+coords,signal);
      if(!zone.properties?.forecastZone)throw Error('Outside confirmed NWS coverage');
      const endpoint=url+'?point='+coords;
      const data=await read(endpoint,signal);
      if(!Array.isArray(data.features))throw Error('Invalid NWS response');
      return {records:data.features.map(f=>point(category,f.id,f.properties.headline||f.properties.event,origin.lat,origin.lon,
        utc(f.properties.sent),received,'REPORTED',{}, {coordinate_role:'Query point in alert area',expires:f.properties.expires},endpoint))};
    }
    case 'earthquakes':return {records:quakes(await read(url,signal),received)};
    case 'satellites':{
      if(!['25544','20580'].includes(catalogId))throw Error('Unsupported satellite');
      return orbit(await read(url.replace('25544',catalogId),signal,true),origin,now,catalogId);
    }
    case 'weather':{
      const endpoint=url+'?latitude='+origin.lat+'&longitude='+origin.lon+'&hourly=temperature_2m&forecast_days=2&timezone=GMT';
      const w=await read(endpoint,signal);
      if(w.hourly_units?.temperature_2m!=='°C'||!Array.isArray(w.hourly?.time)||w.hourly.time.length!==w.hourly.temperature_2m?.length)throw Error('Invalid forecast');
      return {records:w.hourly.time.flatMap((t,i)=>{
        const valid=utc(t+'Z'), temp=measurement(w.hourly.temperature_2m[i],'°C');
        return valid && Date.parse(valid)>=now && temp?[point(category,valid,'2 m temperature',w.latitude,w.longitude,valid,received,'PREDICTED',{temperature:temp},{},endpoint)]:[];
      }).slice(0,24)};
    }
    case 'wildfires':{
      const data=await read(url,signal);
      if(!Array.isArray(data.events))throw Error('Invalid EONET snapshot');
      return {records:data.events.flatMap(e=>{
        const g=e.geometry?.filter(p=>p.type==='Point' && utc(p.date)).sort((a,b)=>Date.parse(b.date)-Date.parse(a.date))[0];
        return g?[point(category,e.id,String(e.title),g.coordinates[1],g.coordinates[0],utc(g.date),received,'REPORTED',{}, {scope:'Latest reported point per open event'},'https://eonet.gsfc.nasa.gov/api/v3/events/'+encodeURIComponent(e.id))]:[];
      })};
    }
    case 'transport':{
      if(greatCircleKm(origin,{lat:40.73,lon:-73.99})>100)throw Error('Outside NYC bike-share coverage');
      const data=await read(url,signal);
      if(!Array.isArray(data.data?.stations))throw Error('Invalid GBFS station list');
      return {records:[...parseStationInformation(data).values()].map(s=>point(category,s.stationId,s.name,s.lat,s.lon,null,received,'REPORTED',{capacity:measurement(s.capacity,'docks')},{scope:'Station inventory; vehicle positions unknown'}))};
    }
    default:throw Error('Unknown source');
  }
}
