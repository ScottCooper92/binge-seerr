package io.github.scottcooper92.binge.seerr.handoff

/**
 * The page's one script (#772), allowed by its hash and nothing else ([PAGE_SCRIPT_HASH]):
 *
 * - It takes the key from the code's URL fragment, which a browser never sends, and keeps it in this tab's session
 *   storage so it survives the page posting the address and following the TV. It then takes the fragment out of the
 *   address bar: a refresh to a URL with a fragment is a jump within the page, not a reload, so the page would stop
 *   following the TV, and the key has no business in the tab's history.
 * - It puts the key into the "Open Seerr" link, so the app gets it without it crossing the LAN. Without the key, as
 *   on a page opened from the typed URL, the app could not send the PIN, so the PIN step hides the link.
 * - On the TV's sign-in step it shows the server's sign-in fields and seals what is typed exactly as the app does
 *   ([HandOffKey.seal]: AES-256-GCM under an HKDF-SHA256 key, with [HandOffKey.context] as associated data: the token
 *   and the address the TV is signing in to, which the page shows above the form), then follows the TV.
 *
 * The sealing is written out here rather than taken from the browser: a page served over plain HTTP from a LAN
 * address is not a secure context, so the browser's own Web Crypto is not available to it.
 */
internal val PAGE_SCRIPT: String =
    """
var Seal=(function(){
"use strict";
function u8(n){return new Uint8Array(n);}
function concat(a,b){var r=u8(a.length+b.length);r.set(a,0);r.set(b,a.length);return r;}
// SHA-256 over bytes.
var SH=[],SK=[];
(function(){var n=2,c=0;function frac(x){return (x-Math.floor(x))*4294967296|0;}
while(c<64){var p=true;for(var d=2;d*d<=n;d++){if(n%d===0){p=false;break;}}
if(p){if(c<8)SH[c]=frac(Math.pow(n,1/2));SK[c]=frac(Math.pow(n,1/3));c++;}n++;}})();
function sha256(msg){
var l=msg.length,bits=l*8,padded=u8(((l+9+63)>>6)<<6);padded.set(msg);padded[l]=0x80;
var dv=new DataView(padded.buffer);dv.setUint32(padded.length-4,bits>>>0);dv.setUint32(padded.length-8,Math.floor(bits/4294967296));
var h=SH.slice(),w=new Array(64);
for(var o=0;o<padded.length;o+=64){
for(var i=0;i<16;i++)w[i]=dv.getUint32(o+i*4);
for(i=16;i<64;i++){var a=w[i-15],b=w[i-2];
var s0=((a>>>7)|(a<<25))^((a>>>18)|(a<<14))^(a>>>3),s1=((b>>>17)|(b<<15))^((b>>>19)|(b<<13))^(b>>>10);
w[i]=(w[i-16]+s0+w[i-7]+s1)|0;}
var A=h[0],B=h[1],C=h[2],D=h[3],E=h[4],F=h[5],G=h[6],H=h[7];
for(i=0;i<64;i++){
var S1=((E>>>6)|(E<<26))^((E>>>11)|(E<<21))^((E>>>25)|(E<<7)),ch=(E&F)^(~E&G),t1=(H+S1+ch+SK[i]+w[i])|0;
var S0=((A>>>2)|(A<<30))^((A>>>13)|(A<<19))^((A>>>22)|(A<<10)),mj=(A&B)^(A&C)^(B&C),t2=(S0+mj)|0;
H=G;G=F;F=E;E=(D+t1)|0;D=C;C=B;B=A;A=(t1+t2)|0;}
h[0]=(h[0]+A)|0;h[1]=(h[1]+B)|0;h[2]=(h[2]+C)|0;h[3]=(h[3]+D)|0;h[4]=(h[4]+E)|0;h[5]=(h[5]+F)|0;h[6]=(h[6]+G)|0;h[7]=(h[7]+H)|0;}
var out=u8(32),ov=new DataView(out.buffer);for(i=0;i<8;i++)ov.setUint32(i*4,h[i]>>>0);return out;}
function hmac(key,msg){
if(key.length>64)key=sha256(key);var k=u8(64);k.set(key);var ip=u8(64),op=u8(64);
for(var i=0;i<64;i++){ip[i]=k[i]^0x36;op[i]=k[i]^0x5c;}
return sha256(concat(op,sha256(concat(ip,msg))));}
// HKDF-SHA256 with a zero salt and one block, as the TV derives it.
function derive(key,info){return hmac(hmac(u8(32),key),concat(info,new Uint8Array([1])));}
// AES-256 encryption of one block.
var SB=u8(256);
(function(){var p=1,q=1;do{p=p^((p<<1)&0xff)^(p&0x80?0x1b:0);q^=q<<1;q^=q<<2;q^=q<<4;q&=0xff;if(q&0x80)q^=0x09;
var x=q^((q<<1)|(q>>>7))^((q<<2)|(q>>>6))^((q<<3)|(q>>>5))^((q<<4)|(q>>>4));SB[p]=(x^0x63)&0xff;}while(p!==1);SB[0]=0x63;})();
function expand(key){var w=u8(240),rc=1;w.set(key);
for(var i=32;i<240;i+=4){var t=[w[i-4],w[i-3],w[i-2],w[i-1]];
if(i%32===0){t=[SB[t[1]]^rc,SB[t[2]],SB[t[3]],SB[t[0]]];rc=(rc<<1)^(rc&0x80?0x11b:0);}
else if(i%32===16){t=[SB[t[0]],SB[t[1]],SB[t[2]],SB[t[3]]];}
for(var j=0;j<4;j++)w[i+j]=w[i-32+j]^t[j];}return w;}
function xt(b){return ((b<<1)^(b&0x80?0x1b:0))&0xff;}
function enc(w,inp){var s=u8(16),t=u8(16),r,c,i;for(i=0;i<16;i++)s[i]=inp[i]^w[i];
for(r=1;r<=14;r++){
for(c=0;c<4;c++)for(i=0;i<4;i++)t[c*4+i]=SB[s[((c+i)%4)*4+i]];
if(r<14){for(c=0;c<4;c++){var a0=t[c*4],a1=t[c*4+1],a2=t[c*4+2],a3=t[c*4+3],x=a0^a1^a2^a3;
s[c*4]=a0^x^xt(a0^a1);s[c*4+1]=a1^x^xt(a1^a2);s[c*4+2]=a2^x^xt(a2^a3);s[c*4+3]=a3^x^xt(a3^a0);}}else{s.set(t);}
for(i=0;i<16;i++)s[i]^=w[r*16+i];}return s;}
// GHASH multiply in GF(2^128).
function gmul(x,y){var z=u8(16),v=y.slice();for(var i=0;i<128;i++){if(x[i>>3]&(0x80>>(i&7))){for(var j=0;j<16;j++)z[j]^=v[j];}
var lsb=v[15]&1;for(j=15;j>0;j--)v[j]=(v[j]>>>1)|((v[j-1]&1)<<7);v[0]>>>=1;if(lsb)v[0]^=0xe1;}return z;}
function ghash(h,aad,ct){var y=u8(16);function blocks(d){for(var o=0;o<d.length;o+=16){for(var i=0;i<16&&o+i<d.length;i++)y[i]^=d[o+i];y=gmul(y,h);}}
blocks(aad);blocks(ct);var len=u8(16),dv=new DataView(len.buffer);dv.setUint32(4,aad.length*8);dv.setUint32(12,ct.length*8);
for(var i=0;i<16;i++)y[i]^=len[i];return gmul(y,h);}
function gcm(key,nonce,pt,aad){var w=expand(key),h=enc(w,u8(16)),j0=u8(16);j0.set(nonce);j0[15]=1;
var ct=u8(pt.length),ctr=j0.slice();
for(var o=0;o<pt.length;o+=16){for(var k=15;k>=12;k--){ctr[k]=(ctr[k]+1)&0xff;if(ctr[k])break;}
var ks=enc(w,ctr);for(var i=0;i<16&&o+i<pt.length;i++)ct[o+i]=pt[o+i]^ks[i];}
var s=ghash(h,aad,ct),ek=enc(w,j0),tag=u8(16);for(i=0;i<16;i++)tag[i]=s[i]^ek[i];return concat(ct,tag);}
function b64u(b){var s="";for(var i=0;i<b.length;i++)s+=String.fromCharCode(b[i]);return btoa(s).replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,"");}
function unb64u(s){var t=atob(s.replace(/-/g,"+").replace(/_/g,"/")),b=u8(t.length);for(var i=0;i<t.length;i++)b[i]=t.charCodeAt(i);return b;}
var te=new TextEncoder();
// What HandOffKey.seal makes: nonce, then ciphertext and tag, as URL-safe text.
function seal(keyText,context,text,nonce){if(!nonce){nonce=u8(12);crypto.getRandomValues(nonce);}
var k=derive(unb64u(keyText),te.encode("seerr-tv-handoff-credentials-v1"));return b64u(concat(nonce,gcm(k,nonce,te.encode(text),te.encode(context))));}
return {seal:seal,sha256:sha256,enc:enc,expand:expand};
})();
(function(){
"use strict";
var T=document.documentElement.getAttribute("data-token");
var m=/^#k=([A-Za-z0-9_-]{43})$/.exec(location.hash),K=m?m[1]:null,S="seerr-handoff-"+T;
try{if(K){sessionStorage.setItem(S,K);}else{K=sessionStorage.getItem(S);}}catch(e){}
if(m&&history.replaceState){history.replaceState(null,"",location.pathname);}
var a=document.getElementById("app");
if(K&&a){a.setAttribute("href",a.getAttribute("href").replace("#Intent;","&k="+K+"#Intent;"));}
var o=document.getElementById("openapp");
if(o&&!K){o.hidden=true;}
var f=document.getElementById("signin");
if(!f)return;
var page="/a/"+T,sel=document.getElementById("mode"),go=document.getElementById("go"),err=document.getElementById("err");
var sent=0,busy=false;
function mode(){return sel?sel.value:f.getAttribute("data-mode");}
function show(){var md=mode(),ds=f.querySelectorAll("[data-for]");
for(var i=0;i<ds.length;i++){ds[i].hidden=ds[i].getAttribute("data-for").split(" ").indexOf(md)<0;}}
function val(id){var e=document.getElementById(id);return e?e.value:"";}
function idle(){busy=false;go.disabled=false;go.textContent=go.getAttribute("data-idle");}
function fail(){sent=0;idle();err.hidden=false;}
if(K){document.getElementById("ontv").hidden=true;f.hidden=false;show();if(sel)sel.addEventListener("change",show);}
f.addEventListener("submit",function(ev){
ev.preventDefault();
if(busy||!K)return;
busy=true;go.disabled=true;err.hidden=true;go.textContent=go.getAttribute("data-busy");
var md=mode(),person=md==="Jellyfin"||md==="Emby";
var c={mode:md,apiKey:md==="ApiKey"?val("apikey"):"",email:md==="Local"?val("email"):"",
username:person?val("username"):"",password:md==="ApiKey"?"":val("password")};
var body="sealed="+encodeURIComponent(Seal.seal(K,T+"\n"+f.getAttribute("data-address"),JSON.stringify(c)));
fetch("/c/"+T,{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded"},body:body})
.then(function(r){return r.ok?r.json():null;})
.then(function(j){if(j&&j.attempt){sent=j.attempt;}else{fail();}},fail);});
function poll(){
fetch("/s/"+T,{cache:"no-store"}).then(function(r){return r.json();}).then(function(s){
if(s.state!=="signin"){location.replace(page);return;}
if(busy&&sent&&s.failed&&s.attempt>=sent){fail();}
},function(){}).then(function(){setTimeout(poll,2000);});}
setTimeout(poll,2000);
})();
    """.trimIndent()
