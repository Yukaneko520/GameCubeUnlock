#include "native-sr-state.h"
#include "sha256.h"
#include <elf.h>
#include <string.h>

namespace gamecube {
static bool mapped(const Elf64_Ehdr* elf,uintptr_t offset,size_t size) {
    const auto* segments=reinterpret_cast<const Elf64_Phdr*>(reinterpret_cast<uintptr_t>(elf)+elf->e_phoff);
    for(int i=0;i<elf->e_phnum;i++) {
        const auto& p=segments[i];
        if(p.p_type==PT_LOAD && (p.p_flags&PF_R) && offset>=p.p_vaddr
                && size<=p.p_memsz && offset-p.p_vaddr<=p.p_memsz-size) return true;
    }
    return false;
}
bool targetIdentity(void* base) { return targetIdentity(base,os6Native); }
bool targetIdentity(void* base,const NativeTarget& contract) {
    if(!base) return false;
    const auto* elf=static_cast<const Elf64_Ehdr*>(base);
    if(memcmp(elf->e_ident,ELFMAG,SELFMAG) || elf->e_ident[EI_CLASS]!=ELFCLASS64
            || elf->e_ident[EI_DATA]!=ELFDATA2LSB || elf->e_machine!=EM_AARCH64
            || elf->e_phoff>4096 || elf->e_phnum>64 || elf->e_phentsize!=sizeof(Elf64_Phdr)
            || elf->e_phoff+elf->e_phnum*sizeof(Elf64_Phdr)>4096) return false;
    const auto* segments=reinterpret_cast<const Elf64_Phdr*>(static_cast<const uint8_t*>(base)+elf->e_phoff);
    Sha256 sha; int count=0;
    for(int i=0;i<elf->e_phnum;i++) {
        const auto& p=segments[i];
        if(p.p_type!=PT_LOAD || !(p.p_flags&PF_R) || (p.p_flags&PF_W)) continue;
        if(count>=2 || p.p_vaddr!=contract.loads[count].address || p.p_filesz!=contract.loads[count].bytes || p.p_memsz<p.p_filesz) return false;
        update(sha,static_cast<const uint8_t*>(base)+p.p_vaddr,p.p_filesz); ++count;
    }
    return count==2 && equalsHex(finish(sha),contract.immutableDigest)
            && mapped(elf,contract.buildIdAddress,sizeof(contract.buildId))
            && !memcmp(static_cast<const uint8_t*>(base)+contract.buildIdAddress,contract.buildId,sizeof(contract.buildId))
            && mapped(elf,contract.getter,16) && mapped(elf,contract.renderer,16) && mapped(elf,contract.buffer,16)
            && mapped(elf,contract.rendererPointer,sizeof(uint64_t))
            && !memcmp(static_cast<const uint8_t*>(base)+contract.getter,contract.getterBytes,16)
            && !memcmp(static_cast<const uint8_t*>(base)+contract.renderer,contract.rendererBytes,16)
            && !memcmp(static_cast<const uint8_t*>(base)+contract.buffer,contract.bufferBytes,16);
}
}
