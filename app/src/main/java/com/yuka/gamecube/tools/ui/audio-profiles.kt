package com.yuka.gamecube.tools.ui

/** GameCube 14.0.10.00_de GameSoundDomesticGame enum. OEM admission stays in LegacyAudio. */
internal fun audioProfiles(): List<ProfileOption> = listOf(
    Triple("com.tencent.lolm", "League of Legends: Wild Rift", 1),
    Triple("com.tencent.tmgp.sgame", "Honor of Kings", 2),
    Triple("com.tencent.tmgp.pubgmhd", "Game for Peace", 3),
    Triple("com.miHoYo.Yuanshen", "Genshin Impact", 4),
    Triple("com.tencent.tmgp.cod", "Call of Duty: Mobile", 5),
    Triple("com.netease.pm02", "Revelation", 6),
    Triple("com.netease.aceracer", "Ace Racer", 7),
    Triple("com.netease.g67", "Diablo Immortal", 8),
    Triple("com.netease.dwrg", "Identity V", 9),
    Triple("com.netease.dfjs.vivo", "Racing Master", 7),
    Triple("com.miHoYo.hkrpg", "Honkai: Star Rail", 4),
    Triple("com.tencent.toaa", "Undawn", 5),
    Triple("com.netease.nshm", "Justice Mobile", 6),
    Triple("com.netease.party", "Eggy Party", 7),
).map { (pkg, name, preset) -> ProfileOption(pkg, name, "Preset $preset") }
