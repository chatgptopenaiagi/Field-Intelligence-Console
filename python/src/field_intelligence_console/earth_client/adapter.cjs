// Headless entry point for the same pinned client code shipped on Android and Windows.
const { query } = require('./core.js');
const [category, lat, lon, satellite] = process.argv.slice(2);
const selection = {lat:Number(lat), lon:Number(lon)};
query(category, selection, AbortSignal.timeout(18000), satellite)
  .then(({records,checked_at,passes=[]}) => {
    process.stdout.write(JSON.stringify({schema:'fic.earth-keypoints',version:1,selection,category,checked_at,records,passes}));
  })
  .catch(error => {
    const message=error.message.includes('seven-day')?'Orbital elements outside the seven-day window.':
      error.message.includes('coverage')?'Source does not cover the selected region.':'Earth provider temporarily unavailable.';
    process.stderr.write(message);process.exitCode=1;
  });
