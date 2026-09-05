package com.aprireader.app.data.profile

import com.aprireader.app.R

enum class AvatarGender { MALE, FEMALE }

enum class AvatarPreset(
    val id: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val drawableRes: Int,
    val gender: AvatarGender,
) {
    // Мужские образы (5)
    M1_SCHOLAR(
        id = "m1_scholar",
        titleRes = R.string.avatar_m1_title,
        descriptionRes = R.string.avatar_m1_desc,
        drawableRes = R.drawable.avatar_m1_scholar,
        gender = AvatarGender.MALE,
    ),
    M2_DETECTIVE(
        id = "m2_detective",
        titleRes = R.string.avatar_m2_title,
        descriptionRes = R.string.avatar_m2_desc,
        drawableRes = R.drawable.avatar_m2_detective,
        gender = AvatarGender.MALE,
    ),
    M3_CYBER(
        id = "m3_cyber",
        titleRes = R.string.avatar_m3_title,
        descriptionRes = R.string.avatar_m3_desc,
        drawableRes = R.drawable.avatar_m3_cyber,
        gender = AvatarGender.MALE,
    ),
    M4_PHILOSOPHER(
        id = "m4_philosopher",
        titleRes = R.string.avatar_m4_title,
        descriptionRes = R.string.avatar_m4_desc,
        drawableRes = R.drawable.avatar_m4_philosopher,
        gender = AvatarGender.MALE,
    ),
    M5_BARD(
        id = "m5_bard",
        titleRes = R.string.avatar_m5_title,
        descriptionRes = R.string.avatar_m5_desc,
        drawableRes = R.drawable.avatar_m5_bard,
        gender = AvatarGender.MALE,
    ),

    // Женские образы (5)
    F1_SORCERESS(
        id = "f1_sorceress",
        titleRes = R.string.avatar_f1_title,
        descriptionRes = R.string.avatar_f1_desc,
        drawableRes = R.drawable.avatar_f1_sorceress,
        gender = AvatarGender.FEMALE,
    ),
    F2_POETESS(
        id = "f2_poetess",
        titleRes = R.string.avatar_f2_title,
        descriptionRes = R.string.avatar_f2_desc,
        drawableRes = R.drawable.avatar_f2_poetess,
        gender = AvatarGender.FEMALE,
    ),
    F3_ADVENTURER(
        id = "f3_adventurer",
        titleRes = R.string.avatar_f3_title,
        descriptionRes = R.string.avatar_f3_desc,
        drawableRes = R.drawable.avatar_f3_adventurer,
        gender = AvatarGender.FEMALE,
    ),
    F4_CYBER_ORACLE(
        id = "f4_cyber_oracle",
        titleRes = R.string.avatar_f4_title,
        descriptionRes = R.string.avatar_f4_desc,
        drawableRes = R.drawable.avatar_f4_cyber_oracle,
        gender = AvatarGender.FEMALE,
    ),
    F5_ARISTOCRAT(
        id = "f5_aristocrat",
        titleRes = R.string.avatar_f5_title,
        descriptionRes = R.string.avatar_f5_desc,
        drawableRes = R.drawable.avatar_f5_aristocrat,
        gender = AvatarGender.FEMALE,
    );

    companion object {
        fun fromId(id: String?): AvatarPreset = entries.firstOrNull { it.id == id } ?: M1_SCHOLAR
    }
}

enum class ReaderTitle(
    val key: String,
    val titleRes: Int,
    val badgeIcon: String,
) {
    BOOK_KEEPER("book_keeper", R.string.title_book_keeper, "📚"),
    NIGHT_READER("night_reader", R.string.title_night_reader, "🌙"),
    LITERARY_WANDERER("wanderer", R.string.title_wanderer, "🧭"),
    BIBLIOPHILE("bibliophile", R.string.title_bibliophile, "✨"),
    SEEKER_OF_TRUTH("seeker", R.string.title_seeker, "🔮"),
    MASTER_OF_STORIES("master", R.string.title_master, "👑");

    companion object {
        fun fromKey(key: String?): ReaderTitle = entries.firstOrNull { it.key == key } ?: BOOK_KEEPER
    }
}
