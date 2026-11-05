#pragma once
#include <stdint.h>
#include <stddef.h>

namespace gamecube {
// FIPS180-4 SHA256; also evaluated at compile time against standard test vectors.
struct Sha256 {
    uint32_t h[8]={0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19};
    uint8_t buffer[64]{};
    size_t used=0;
    uint64_t bytes=0;
};
struct Digest { uint8_t bytes[32]{}; };
constexpr uint32_t rotate(uint32_t x,int n) { return (x>>n)|(x<<(32-n)); }
constexpr void transform(Sha256& s) {
    constexpr uint32_t k[64]={
        0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2};
    uint32_t w[64]{};
    for(int i=0;i<16;i++) w[i]=(uint32_t(s.buffer[4*i])<<24)|(uint32_t(s.buffer[4*i+1])<<16)
            |(uint32_t(s.buffer[4*i+2])<<8)|s.buffer[4*i+3];
    for(int i=16;i<64;i++) w[i]=w[i-16]+(rotate(w[i-15],7)^rotate(w[i-15],18)^(w[i-15]>>3))
            +w[i-7]+(rotate(w[i-2],17)^rotate(w[i-2],19)^(w[i-2]>>10));
    uint32_t a=s.h[0],b=s.h[1],c=s.h[2],d=s.h[3],e=s.h[4],f=s.h[5],g=s.h[6],h=s.h[7];
    for(int i=0;i<64;i++) {
        uint32_t t=h+(rotate(e,6)^rotate(e,11)^rotate(e,25))+((e&f)^(~e&g))+k[i]+w[i];
        uint32_t u=(rotate(a,2)^rotate(a,13)^rotate(a,22))+((a&b)^(a&c)^(b&c));
        h=g;g=f;f=e;e=d+t;d=c;c=b;b=a;a=t+u;
    }
    s.h[0]+=a;s.h[1]+=b;s.h[2]+=c;s.h[3]+=d;s.h[4]+=e;s.h[5]+=f;s.h[6]+=g;s.h[7]+=h;
}
constexpr void append(Sha256& s,uint8_t byte) {
    s.buffer[s.used++]=byte; ++s.bytes;
    if(s.used==64) { transform(s); s.used=0; }
}
inline void update(Sha256& s,const uint8_t* bytes,size_t size) {
    for(size_t i=0;i<size;i++) append(s,bytes[i]);
}
constexpr Digest finish(Sha256 s) {
    uint64_t bits=s.bytes*8;
    append(s,0x80);
    while(s.used!=56) append(s,0);
    for(int shift=56;shift>=0;shift-=8) append(s,static_cast<uint8_t>(bits>>shift));
    Digest out;
    for(int i=0;i<32;i++) out.bytes[i]=static_cast<uint8_t>(s.h[i/4]>>(24-8*(i%4)));
    return out;
}
constexpr Digest textDigest(const char* text) {
    Sha256 s; for(size_t i=0;text[i];i++) append(s,static_cast<uint8_t>(text[i])); return finish(s);
}
constexpr bool equalsHex(Digest d,const char* hex) {
    const char* digits="0123456789abcdef";
    for(int i=0;i<32;i++) if(hex[2*i]!=digits[d.bytes[i]>>4] || hex[2*i+1]!=digits[d.bytes[i]&15]) return false;
    return hex[64]==0;
}
static_assert(equalsHex(textDigest(""),"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
static_assert(equalsHex(textDigest("abc"),"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
static_assert(equalsHex(textDigest("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1"));
}
