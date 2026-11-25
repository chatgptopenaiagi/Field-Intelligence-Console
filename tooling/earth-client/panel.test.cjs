// Run against the unpacked client from a candidate with playwright-core and Edge installed.
const assert = require('node:assert/strict');
const path = require('node:path');
const {pathToFileURL} = require('node:url');
const {chromium} = require('playwright-core');

async function run() {
  const target = pathToFileURL(path.resolve(process.argv[2] || 'python/src/field_intelligence_console/earth_client/index.html'));
  const browser = await chromium.launch({channel:'msedge',headless:true});
  try {
    const ctx = await browser.newContext({viewport:{width:320,height:720}});
    const page = await ctx.newPage(), errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    const now = Date.now(), recent = new Date(now + 3600000);
    const quake = (id,lon,mag,age) => ({id,geometry:{type:'Point',coordinates:[lon,0,null]},
      properties:{mag,time:now-age*3600000,place:id}});
    await ctx.route('https://earthquake.usgs.gov/**',route=>route.fulfill({json:{features:[
      quake('Synthetic A',0,4,0.1),quake('Synthetic B',3,5.3,10)
    ]}}));
    let failWeather=false;
    await ctx.route('https://api.open-meteo.com/**',route=>failWeather?route.abort():route.fulfill({
      json:{latitude:0,longitude:0,hourly_units:{temperature_2m:'°C'},hourly:{
        time:[recent,new Date(+recent+3600000)].map(d=>d.toISOString().slice(0,16)),temperature_2m:[0,-2]}}
    }));
    const requests=[];
    page.on('request',r=>{if(r.url().startsWith('https:'))requests.push(r.url());});
    await page.goto(target+'#lat=0&lon=0&lang=ro');
    assert.equal(await page.locator('#refresh').innerText(),'Explorează');
    assert.equal(requests.length,0);
    await page.click('#refresh');
    await page.waitForFunction(()=>document.querySelectorAll('#results button').length===2);
    assert.match(await page.locator('#card').innerText(),/Adâncime · Necunoscut/);
    assert.match(await page.locator('#original').getAttribute('href'),/2.5_day.geojson$/);
    const popupPromise=page.waitForEvent('popup');
    await page.click('#original');const popup=await popupPromise;await popup.waitForLoadState();
    assert.match(popup.url(),/2.5_day.geojson$/);await popup.close();
    assert.equal(new URL(page.url()).protocol,'file:');
    await page.click('#filters');
    for(const [id,v] of [['mag','5'],['age','1'],['mag','2.5'],['age','0'],['range','100']]){
      await page.selectOption('#'+id,v);
      const expected=id==='age'&&v==='1'?0:id==='age'&&v==='0'?2:1;
      assert.equal(await page.locator('#results button').count(),expected);
    }
    await page.selectOption('#range','0');
    await page.locator('#results button').nth(1).click();
    assert.match(await page.locator('#card h2').innerText(),/Synthetic B/);
    await page.click('#analysisLabel');assert.match(await page.locator('#preview').inputValue(),/USGS/);
    await page.fill('#preview','Reviewed synthetic record');assert.equal(await page.evaluate(()=>ficReviewed()),'Reviewed synthetic record');
    await page.selectOption('#topic','weather');assert.equal(await page.evaluate(()=>ficReviewed()),'');
    assert.match(await page.locator('#original').getAttribute('href'),/latitude=0&longitude=0&hourly=temperature_2m/);
    await page.click('#refresh');await page.waitForSelector('#forecast');
    assert.match(await page.locator('#chart').getAttribute('aria-label'),/-2 – 0 °C/);
    assert.equal(await page.locator('#results button').count(),2);
    failWeather=true;await page.click('#refresh');
    await page.waitForFunction(()=>document.querySelector('#status').textContent.includes('temporar indisponibilă'));
    assert.equal(await page.locator('#forecast').isVisible(),false);
    assert.equal(await page.evaluate(()=>ficExport()),'');
    await page.selectOption('#topic','infrastructure');await page.click('#refresh');
    assert.match(await page.locator('#status').innerText(),/nu acoperă regiunea/);
    await page.click('#region');await page.waitForSelector('#card');assert.match(await page.locator('#card').innerText(),/46042/);
    await ctx.setOffline(true);
    await page.selectOption('#topic','aviation');await page.click('#refresh');await page.waitForSelector('#card');
    const doc=await page.evaluate(()=>JSON.parse(ficExport()));
    assert.equal(doc.records.length,20);assert.ok(doc.records.every(r=>r.kind==='HISTORICAL' && r.limitations));
    const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
    assert.equal(overflow,false);
    await page.selectOption('#language','de');await page.waitForFunction(()=>document.documentElement.lang==='de');
    assert.equal(await page.locator('#refresh').innerText(),'Erkunden');
    assert.deepEqual(errors,[]);
    console.log(JSON.stringify({status:'PASS',viewport:320,source_popup:true,filters:3,review:true,forecast:true,
      weather_failure:true,regional_switch:true,offline_archive:doc.records.length,language:'de',horizontal_overflow:overflow}));
  } finally {await browser.close();}
}
run().catch(e=>{console.error(e);process.exitCode=1;});
