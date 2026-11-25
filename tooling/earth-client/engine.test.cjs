const {test}=require('node:test');
const assert=require('node:assert/strict');
const core=require('../../python/src/field_intelligence_console/earth_client/core.js');
const at = '2023-08-30T00:00:00Z';
test('USGS uses lon/lat, validates the snapshot and preserves unknown depth',()=>{
  const f={id:'synthetic',geometry:{type:'Point',coordinates:[15,48,null]},properties:{mag:3,time:0}};
  const [r]=core.quakes({features:[f]},at);
  assert.equal(r.latitude,48);assert.equal(r.longitude,15);assert.equal(r.measurements.depth,null);
  assert.equal(r.kind,'REPORTED');assert.equal(r.freshness,'older-than-24h');
  assert.throws(()=>core.quakes({features:[f,f]},at));assert.throws(()=>core.quakes({},at));
});
test('ISS predictions are time-bounded and observer-specific',()=>{
  const tle='ISS (ZARYA)\n1 25544U 98067A   24123.50000000  .00016717  00000-0  30100-3 0  9999\n2 25544  51.6400 220.0000 0004000  80.0000 280.0000 15.50000000450000';
  const now=Date.parse('2024-05-02T12:00:00Z');
  const q=core.orbit(tle,{lat:48,lon:16},now);
  assert.equal(q.records.length,31);assert.ok(q.records.every(r=>r.kind==='PREDICTED' && r.observed_at===null && r.valid_at));
  assert.ok(q.passes.length>0 && q.passes.every(p=>p.max_elevation_deg>=10));assert.throws(()=>core.orbit(tle,{lat:48,lon:16},now+8*86400000));
  assert.throws(()=>core.orbit(tle,{lat:0,lon:0},now,'20580'));
});
test('archived aircraft and expedition have independent dated records',async()=>{
  const offline={lat:0,lon:0};
  const a=await core.query('aviation',offline),m=await core.query('maritime',offline);
  assert.equal(a.records.length,20);assert.equal(m.records.length,2);
  assert.ok(a.records.every(r=>r.kind==='HISTORICAL' && r.metadata.speed===null));
  await assert.rejects(core.query('transport',offline),/coverage/);
  await assert.rejects(core.query('earthquakes',{lat:91,lon:0}),/location/);
  assert.ok(core.globeMesh().indices.length>2000);
});
