// One static Cesium ellipsoid, one relief texture, no tile streaming or animation timer.
function earthGPU(canvas, image) {
  const gl=canvas.getContext('webgl',{alpha:false,antialias:false,powerPreference:'low-power'});
  if(!gl)return null;
  const vs='attribute vec3 p; uniform mat3 camera; uniform vec2 scale; varying vec3 world; void main(){world=p; vec3 v=camera*p;gl_Position=vec4(v.xy*scale,-v.z*.75,1.0);}';
  const fs='precision mediump float; varying vec3 world; uniform sampler2D relief; void main(){vec3 n=normalize(world); vec2 uv=vec2(0.5+atan(n.y,n.x)/6.2831853,0.5-asin(clamp(n.z,-1.,1.))/3.14159265); gl_FragColor=texture2D(relief,uv);}';
  const program=gl.createProgram();
  for(const [type,source] of [[gl.VERTEX_SHADER,vs],[gl.FRAGMENT_SHADER,fs]]){
    const shader=gl.createShader(type);gl.shaderSource(shader,source);gl.compileShader(shader);
    if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))return null;gl.attachShader(program,shader);gl.deleteShader(shader);
  }
  gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))return null;
  gl.useProgram(program);
  const mesh=FicEarth.globeMesh(),loc=gl.getAttribLocation(program,'p');
  gl.bindBuffer(gl.ARRAY_BUFFER,gl.createBuffer());gl.bufferData(gl.ARRAY_BUFFER,mesh.vertices,gl.STATIC_DRAW);
  gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,3,gl.FLOAT,false,0,0);
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,gl.createBuffer());gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,mesh.indices,gl.STATIC_DRAW);
  gl.bindTexture(gl.TEXTURE_2D,gl.createTexture());gl.texImage2D(gl.TEXTURE_2D,0,gl.RGB,gl.RGB,gl.UNSIGNED_BYTE,image);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
  gl.enable(gl.DEPTH_TEST);gl.clearColor(.035,.082,.106,1);
  gl.uniform2f(gl.getUniformLocation(program,'scale'),.95*canvas.height/canvas.width,.95);
  const camera=gl.getUniformLocation(program,'camera');
  return ({lat,lon})=>{
    if(gl.isContextLost())return false;
    const a=lat*Math.PI/180,b=lon*Math.PI/180,ca=Math.cos(a),sa=Math.sin(a),cb=Math.cos(b),sb=Math.sin(b);
    gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
    gl.uniformMatrix3fv(camera,false,[-sb,-sa*cb,ca*cb,cb,-sa*sb,ca*sb,0,ca,sa]);
    gl.drawElements(gl.TRIANGLES,mesh.indices.length,gl.UNSIGNED_SHORT,0);return true;
  };
}
