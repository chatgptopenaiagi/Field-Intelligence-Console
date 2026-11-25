// A frame is requested only for a change of view, selection, data or GPU context.
function earthMap(canvas, origin, select, modeChanged) {
  const ctx = canvas.getContext('2d'), gpuCanvas = document.getElementById('gpu');
  const {width, height} = canvas, radius = height * .475, rad = Math.PI / 180;
  const wrap = angle => ((angle + 180) % 360 + 360) % 360 - 180;
  let camera = {...origin}, records = [], selected = null, track = false;
  let flat = false, gpu = null, frame = 0, hitPoints = [], drag = null;
  const img = new Image();

  function schedule() { if (!frame) frame = requestAnimationFrame(draw); }
  function setGpu() {
    try { gpu = earthGPU(gpuCanvas, img); } catch { gpu = null; }
    if (!gpu) flat = true;
    modeChanged(flat, !gpu);
    schedule();
  }
  gpuCanvas.addEventListener('webglcontextlost', event => {
    event.preventDefault();
    gpu = null; flat = true; modeChanged(true, true); schedule();
  });
  gpuCanvas.addEventListener('webglcontextrestored', setGpu);
  img.onload = setGpu;
  img.src = FicEarth.texture;
  function project(lat, lon) {
    if (flat) return [width / 2 + width * wrap(lon-camera.lon)/360, height / 2 - height * (lat-camera.lat)/180];
    const a=lat*rad, b=camera.lat*rad, d=(lon-camera.lon)*rad;
    if (Math.sin(a)*Math.sin(b)+Math.cos(a)*Math.cos(b)*Math.cos(d)<0) return null;
    return [width/2+radius*Math.cos(a)*Math.sin(d), height/2-radius*(Math.sin(a)*Math.cos(b)-Math.cos(a)*Math.sin(b)*Math.cos(d))];
  }
  function dot(pos,color,size) {
    if (!pos) return;
    ctx.beginPath(); ctx.arc(...pos,size,0,2*Math.PI); ctx.fillStyle=color;ctx.fill();
    ctx.strokeStyle='#14222b';ctx.lineWidth=1;ctx.stroke();
  }
  function draw() {
    frame=0;
    const accelerated=!flat && gpu && gpu(camera);
    if (gpu && !accelerated && !flat) { flat=true;modeChanged(true,true); }
    canvas.dataset.renderer=accelerated?'cesium-webgl':'2d';
    ctx.clearRect(0,0,width,height);
    if (!accelerated) {
      ctx.fillStyle='#09151b';ctx.fillRect(0,0,width,height);
      if(img.naturalWidth) {
        const x=-camera.lon*width/360, y=camera.lat*height/180;
        for(const offset of [-width,0,width]) ctx.drawImage(img,x+offset,y,width,height);
      }
    }
    hitPoints=records.map(r=>({record:r,pos:project(r.latitude,r.longitude)}));
    if(track) {
      ctx.beginPath();let previous=null;
      for(const {pos,record} of hitPoints) {
        const contiguous=previous && Math.abs(pos?.[0]-previous.pos[0])<width/2 &&
          Date.parse(record.valid_at)-Date.parse(previous.time)===180000;
        if(pos) { if(contiguous) ctx.lineTo(...pos); else ctx.moveTo(...pos); }
        previous=pos?{pos,time:record.valid_at}:null;
      }
      ctx.strokeStyle='#ffcc72';ctx.lineWidth=2;ctx.stroke();
    }
    for(const {pos,record} of hitPoints) dot(pos,record.id===selected?'#fff':'#ffca76',record.id===selected?5:3);
    const place=project(origin.lat,origin.lon);
    if(place){ctx.beginPath();ctx.arc(...place,7,0,2*Math.PI);ctx.strokeStyle='#50ffe0';ctx.lineWidth=2;ctx.stroke();}
  }
  canvas.onpointerdown=e=>{ drag={x:e.clientX,y:e.clientY,lat:camera.lat,lon:camera.lon,moved:false};canvas.setPointerCapture(e.pointerId); };
  canvas.onpointermove=e=>{
    if(!drag)return;
    const dx=e.clientX-drag.x,dy=e.clientY-drag.y;
    if(Math.hypot(dx,dy)>4)drag.moved=true;
    if(drag.moved){
      camera={lat:Math.max(-90,Math.min(90,drag.lat+dy*180/canvas.clientHeight)),
        lon:wrap(drag.lon-dx*360/canvas.clientWidth)};schedule();
    }
  };
  canvas.onpointercancel=()=>{drag=null;};
  canvas.onpointerup=e=>{
    if(drag && !drag.moved) {
      const rect=canvas.getBoundingClientRect(),x=(e.clientX-rect.left)*width/rect.width,y=(e.clientY-rect.top)*height/rect.height;
      let nearest=null, best=16*width/rect.width;
      for(const {record,pos} of hitPoints) if(pos) {
        const d=Math.hypot(x-pos[0],y-pos[1]);if(d<best){best=d;nearest=record;}
      }
      if(nearest)select(nearest);
    }
    drag=null;
  };
  document.addEventListener('visibilitychange',()=>{if(!document.hidden)schedule();});
  schedule();
  return {
    show(list,drawTrack=false){records=list;track=drawTrack;schedule();},
    center(p){camera={...p};schedule();},
    select(id){selected=id;schedule();},
    setOrigin(p){origin=p;camera={...p};schedule();},
    toggle(){if(gpu)flat=!flat;modeChanged(flat,!gpu);schedule();},
    get flat(){return flat;}
  };
}
