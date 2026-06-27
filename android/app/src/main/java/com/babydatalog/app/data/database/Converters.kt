package com.babydatalog.app.data.database

import androidx.room.TypeConverter
import com.babydatalog.app.data.database.entity.BabyState
import com.babydatalog.app.data.database.entity.BreastSide
import com.babydatalog.app.data.database.entity.LatchQuality
import com.babydatalog.app.data.database.entity.MilestoneCategory
import com.babydatalog.app.data.database.entity.NappyAmount
import com.babydatalog.app.data.database.entity.NappyType
import com.babydatalog.app.data.database.entity.PooColour

class Converters {

    // BreastSide
    @TypeConverter
    fun fromBreastSide(value: BreastSide): String = value.name

    @TypeConverter
    fun toBreastSide(value: String): BreastSide = BreastSide.valueOf(value)

    // BabyState (nullable)
    @TypeConverter
    fun fromBabyState(value: BabyState?): String? = value?.name

    @TypeConverter
    fun toBabyState(value: String?): BabyState? = value?.let { BabyState.valueOf(it) }

    // LatchQuality (nullable)
    @TypeConverter
    fun fromLatchQuality(value: LatchQuality?): String? = value?.name

    @TypeConverter
    fun toLatchQuality(value: String?): LatchQuality? = value?.let { LatchQuality.valueOf(it) }

    // NappyType
    @TypeConverter
    fun fromNappyType(value: NappyType): String = value.name

    @TypeConverter
    fun toNappyType(value: String): NappyType = NappyType.valueOf(value)

    // NappyAmount
    @TypeConverter
    fun fromNappyAmount(value: NappyAmount): String = value.name

    @TypeConverter
    fun toNappyAmount(value: String): NappyAmount = NappyAmount.valueOf(value)

    // PooColour (nullable)
    @TypeConverter
    fun fromPooColour(value: PooColour?): String? = value?.name

    @TypeConverter
    fun toPooColour(value: String?): PooColour? = value?.let { PooColour.valueOf(it) }

    // MilestoneCategory
    @TypeConverter
    fun fromMilestoneCategory(value: MilestoneCategory): String = value.name

    @TypeConverter
    fun toMilestoneCategory(value: String): MilestoneCategory = MilestoneCategory.valueOf(value)
}
