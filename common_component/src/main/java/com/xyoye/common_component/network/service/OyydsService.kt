package com.xyoye.common_component.network.service

import com.xyoye.data_component.data.*
import retrofit2.http.*

/**
 *  oyyds 弹幕源
 *  @author maybe
 *  2023-12-07
 */
interface OyydsService {
    @GET("/api//danmaku/search")
    suspend fun searchDanmu(
        @Query("keyword") keyword: String,
        @Query("epNumber") epNumber: String
    ): OyydsDanmuSearchData
}