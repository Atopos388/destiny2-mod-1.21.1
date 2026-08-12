#version 150
uniform vec3 CameraPos;
uniform float Time;
uniform float Heat;
uniform float Opacity;
uniform float Shell;
in vec4 vertexColor;
in vec3 spherePos;
out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}
float noise(vec3 p) {
    vec3 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float fbm(vec3 p) {
    float v=0.0, a=0.56;
    for(int i=0;i<4;i++){v+=noise(p)*a;p=p*2.03+vec3(6.7,9.3,12.1);a*=0.47;}
    return v;
}
bool intersectSphere(vec3 ro, vec3 rd, out float nearT, out float farT) {
    float b=dot(ro,rd), c=dot(ro,ro)-1.0, h=b*b-c;
    if(h<0.0)return false; h=sqrt(h); nearT=-b-h; farT=-b+h; return true;
}
void main() {
    vec3 surface=normalize(spherePos);
    vec3 rd=normalize(surface-CameraPos);
    float nearT,farT;
    if(!intersectSphere(CameraPos,rd,nearT,farT))discard;
    nearT=max(nearT,0.0);
    float thickness=max(farT-nearT,0.0);
    float impact=length(cross(CameraPos,rd));
    vec3 flow=surface*vec3(4.1,3.0,4.1)+vec3(Time*0.29,-Time*1.7,-Time*0.21);
    float coarse=fbm(flow);
    float detail=fbm(flow*1.91+vec3(-Time*0.8,Time*0.43,Time*0.57));
    float turbulence=coarse*0.66+detail*0.34+sin(surface.y*13.0-Time*5.2+coarse*4.8)*0.10;
    float irregularEdge=0.88+(coarse-0.5)*0.20+(detail-0.5)*0.11;
    float baseEdge=smoothstep(1.02,irregularEdge,impact);
    float breakup=smoothstep(0.31+impact*0.22,0.62+impact*0.10,turbulence);
    float edge=baseEdge*mix(1.0,breakup,smoothstep(0.48,0.96,impact));
    float center=smoothstep(0.88,0.04,impact);
    float rim=smoothstep(0.74,0.99,impact)*smoothstep(1.03,0.91,impact);
    vec3 ember=vec3(0.78,0.025,0.002);
    vec3 orange=vec3(1.0,0.24,0.008);
    vec3 gold=vec3(1.0,0.72,0.13);
    vec3 whiteGold=vec3(1.0,0.985,0.80);
    float hot=clamp(Heat/2.8,0.0,1.0);
    vec3 color=mix(ember,orange,clamp(turbulence+center*0.22,0.0,1.0));
    color=mix(color,gold,smoothstep(0.25,0.72,hot));
    color=mix(color,whiteGold,smoothstep(0.70,1.0,hot)*center);
    color*=0.84+center*0.76+turbulence*0.34;
    float volumeAlpha=(0.025+thickness*0.052+turbulence*0.15+center*0.048)*edge;
    float shellAlpha=rim*(0.20+detail*0.34);
    float alpha=mix(volumeAlpha,shellAlpha,Shell)*Opacity;
    if(alpha<0.004)discard;
    fragColor=vec4(color*vertexColor.rgb,clamp(alpha*vertexColor.a,0.0,0.92));
}
