// Redraw on input only. The small fixed backing buffer bounds memory on older phones.
function earthMap(canvas, origin) {
  const ctx=canvas.getContext('2d'), width=canvas.width, height=canvas.height;
  const rad=Math.PI/180, radius=height*.475;
  let camera={...origin}, records=[],flat=false,pixels=null,gpu=null,frame=0,last=null;
  const img=new Image();
  img.onload=()=>{
    const off=document.createElement('canvas');off.width=img.width;off.height=img.height;
    const c=off.getContext('2d');c.drawImage(img,0,0);pixels=c.getImageData(0,0,img.width,img.height).data;
    try{gpu=earthGPU(document.getElementById("gpu"),img,schedule);}catch{gpu=null;}
    schedule();
  };
  img.src=FicEarth.texture;
  function draw(){
    frame=0;if(!ctx)return;
    const accelerated=!flat && gpu && gpu(camera);
    canvas.dataset.renderer=flat?'2d':accelerated?'cesium-webgl':'canvas-globe';
    if(accelerated)ctx.clearRect(0,0,width,height);else{ctx.fillStyle='#09151b';ctx.fillRect(0,0,width,height);}
    const phi=camera.lat*rad,lambda=camera.lon*rad,sp=Math.sin(phi),cp=Math.cos(phi);
    if(flat && pixels)ctx.drawImage(img,0,0,width,height);
    if(!flat && !accelerated){
      const side=Math.ceil(2*radius),raster=ctx.createImageData(side,side);
      for(let row=0;row<side;row++)for(let col=0;col<side;col++){
        const x=(col-radius)/radius,y=(radius-row)/radius,z2=1-x*x-y*y;
        if(z2<0)continue;
        const z=Math.sqrt(z2),lat=Math.asin(Math.max(-1,Math.min(1,y*cp+z*sp)));
        const lon=lambda+Math.atan2(x,z*cp-y*sp);
        const tx=Math.floor(((lon/(2*Math.PI)+1.5)%1)*img.width);
        const ty=Math.min(img.height-1,Math.max(0,Math.floor((.5-lat/Math.PI)*img.height)));
        const src=(ty*img.width+tx)*4,dst=(row*side+col)*4;
        for(let i=0;i<3;i++)raster.data[dst+i]=pixels?pixels[src+i]:[46,87,113][i];
        raster.data[dst+3]=255;
      }
      ctx.putImageData(raster,Math.round(width/2-radius),Math.round(height/2-radius));
    }
    function mark(lat,lon,color,size){
      const a=lat*rad,d=lon*rad-lambda;
      if(!flat && Math.sin(a)*sp+Math.cos(a)*cp*Math.cos(d)<0)return;
      const x=flat?(lon+180)/360*width:width/2+radius*Math.cos(a)*Math.sin(d);
      const y=flat?(90-lat)/180*height:height/2-radius*(Math.sin(a)*cp-Math.cos(a)*sp*Math.cos(d));
      ctx.beginPath();ctx.arc(x,y,size,0,2*Math.PI);ctx.fillStyle=color;ctx.fill();ctx.strokeStyle='#17232e';ctx.stroke();
    }
    for(const r of records)mark(r.latitude,r.longitude,'#ffca76',3);
    mark(origin.lat,origin.lon,'#4fffd5',5);
  }
  function schedule(){if(!frame)frame=requestAnimationFrame(draw);}
  canvas.onpointerdown=e=>{last=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId);};
  canvas.onpointermove=e=>{if(last&&!flat){camera.lon=((camera.lon+(last[0]-e.clientX)*.55+540)%360)-180;
    camera.lat=Math.max(-90,Math.min(90,camera.lat+(e.clientY-last[1])*.5));last=[e.clientX,e.clientY];schedule();}};
  canvas.onpointerup=canvas.onpointercancel=()=>{last=null};schedule();
  return {show(list){records=list;schedule();},center(p){camera={...p};schedule();},setOrigin(p){origin=p;camera={...p};schedule();},
    toggle(){flat=!flat;schedule();return flat;},get flat(){return flat;}};
}
