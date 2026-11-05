#pragma once
#include <stdint.h>
#include <stddef.h>
#include "native-sr-contract.h"

namespace gamecube {
struct GameConfig { uint32_t width,algorithm,flags; };
static_assert(sizeof(GameConfig)==12 && offsetof(GameConfig,algorithm)==4 && offsetof(GameConfig,flags)==8);
struct Policy {
    bool enabled=false;
    int algorithm=0,width=0,source=-1;
    float sharpness=2.5f,dark=-40.f,white=20.f;
    int64_t revision=0;
};
constexpr GameConfig overlay(GameConfig original,Policy p) {
    if(!p.enabled) return original;
    if(p.algorithm) original.algorithm=p.algorithm;
    if(p.width) original.width=p.width;
    if(p.source>=0) original.flags=(original.flags&~1u)|static_cast<uint32_t>(p.source);
    return original;
}
static_assert(overlay({1080,1,6},{}).width==1080);
static_assert(overlay({1080,1,6},{true,4,1260,1}).flags==7);
static_assert(overlay({1080,1,7},{true,0,0,0}).algorithm==1);
static_assert(overlay({1080,1,7},{true,0,0,0}).flags==6);
struct Candidate { void* base=nullptr; char path[1024]{}; };
using HookFunction=int(*)(void*,void*,void**);
using UnhookFunction=int(*)(void*);
struct NativeApi { uint32_t version; HookFunction hook; UnhookFunction unhook; };
using LibraryCallback=void(*)(const char*,void*);
Policy policy();
void configure(Policy value);
void metadata(const char* package,const char* build);
void log(const char* format,...) __attribute__((format(printf,1,2)));
Candidate candidate();
void discoverLoaded();
int install(const char* path,const char* digest);
int status();
bool targetIdentity(void* base);
bool targetIdentity(void* base,const NativeTarget& contract);
bool selectTarget(int sdk);
extern HookFunction hookFunction;
bool installFunctions(void* base,const NativeTarget& contract);
}
