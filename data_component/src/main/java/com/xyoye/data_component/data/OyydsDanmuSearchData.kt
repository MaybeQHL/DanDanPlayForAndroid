package com.xyoye.data_component.data

import android.os.Parcelable
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

/**
 * Created by xyoye on 2020/11/26.
 */

@Parcelize
@JsonClass(generateAdapter = true)
data class OyydsDanmuSearchData(
    val code:Int,
    val msg: String,
//    val data: MutableList<OyydsDanmuAnimeData>?
    val data:OyydsDanmuData
    ) : Parcelable

@Parcelize
@JsonClass(generateAdapter = true)
data class OyydsDanmuData(
    val data: MutableList<String>?,
    val total:Int,
    val epNumber:String
) : Parcelable


//@Parcelize
//@JsonClass(generateAdapter = true)
//data class OyydsDanmuItemData(
//    val animeId: Int,
//    val animeTitle: String?,
//    val type: String?,
//    val episodes: MutableList<DanmuEpisodeData>?
//) : Parcelable