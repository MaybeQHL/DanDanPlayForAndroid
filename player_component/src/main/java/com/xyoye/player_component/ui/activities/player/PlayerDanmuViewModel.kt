package com.xyoye.player_component.ui.activities.player

import android.net.Uri
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.alibaba.fastjson.JSONWriter
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.extension.isValid
import com.xyoye.common_component.extension.toFile
import com.xyoye.common_component.network.Retrofit
import com.xyoye.common_component.network.request.httpRequest
import com.xyoye.common_component.source.base.BaseVideoSource
import com.xyoye.common_component.utils.DanmuUtils
import com.xyoye.common_component.utils.FileHashUtils
import com.xyoye.common_component.utils.IOUtils
import com.xyoye.common_component.utils.JsonHelper
import com.xyoye.common_component.utils.comparator.FileNameComparator
import com.xyoye.common_component.utils.getFileNameNoExtension
import com.xyoye.data_component.bean.DanmuSourceContentBean
import com.xyoye.data_component.bean.LoadDanmuBean
import com.xyoye.data_component.data.DanmuAnimeData
import com.xyoye.data_component.enums.LoadDanmuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLDecoder

/**
 * Created by xyoye on 2022/1/2.
 */

data class OyydsSearchData(
    var keyword: String,
    var epNumber: String
)

class PlayerDanmuViewModel : BaseViewModel() {
    val loadDanmuLiveData = MutableLiveData<LoadDanmuBean>()
    val danmuSearchLiveData = MutableLiveData<List<DanmuSourceContentBean>>()
    val downloadDanmuLiveData = MutableLiveData<Pair<String, Int>?>()

    fun loadDanmu(videoSource: BaseVideoSource) {
        val loadResult = LoadDanmuBean(videoSource.getVideoUrl())
        val historyDanmuPath = videoSource.getDanmuPath()

        viewModelScope.launch(Dispatchers.IO) {
            //如果弹幕内容不为空，则无需匹配弹幕
            if (historyDanmuPath.toFile().isValid()) {
                loadResult.state = LoadDanmuState.NO_MATCH_REQUIRE
                loadResult.danmuPath = historyDanmuPath
                loadResult.episodeId = videoSource.getEpisodeId()
                loadDanmuLiveData.postValue(loadResult)
                return@launch
            }
            //根据弹幕路径选择合适弹幕匹配方法
            val videoUrl = videoSource.getVideoUrl()
            val uri = Uri.parse(videoUrl)
            when (uri.scheme) {
                "http", "https" -> {
                    loadNetworkDanmu(videoSource)
                }

                "file", "content" -> {
                    loadLocalDanmu(videoUrl)
                }

                else -> {
                    //本地视频的绝对路径，例：/storage/emulate/0/Download/test.mp4
                    if (videoUrl.startsWith("/")) {
                        loadLocalDanmu(videoUrl)
                    } else {
                        loadDanmuLiveData.postValue(loadResult)
                    }
                }
            }
        }
    }


    /**
     * 获取oyyds搜索弹幕的结构
     * @author maybe
     * 2023-12-11 15:18:22
     */
    private fun getOyydsSearchData(videoPath: String): OyydsSearchData? {

        var keyword = "";
        var epNumber = ""
        var filename = ""
        var decodedString = ""
        var lastPart = ""
        var names: List<String>


        filename = getFileNameNoExtension(videoPath)
        // 解码字符串
        decodedString = URLDecoder.decode(filename, "UTF-8")
        // 获取最后一个 "/" 后面的内容
        lastPart = decodedString.substringAfterLast("/")

        //兼容纯纯看番下载文件
        if (videoPath.contains("EasyBangumi", true)) {
            names = lastPart.split("-")
            keyword = names[0]
            epNumber = names[2]
            epNumber = names[2]
        } else if (lastPart.contains("-")) {
            names = lastPart.split("-")
            keyword = names[0]
            epNumber = names[1]
        } else {
            return null
        }

        val oyydsSearchData = OyydsSearchData(keyword, epNumber)
        Log.d("oyydsSearchData", oyydsSearchData.toString())
        return oyydsSearchData
    }

    private suspend fun loadLocalDanmu(videoUrl: String) {
        val loadResult = LoadDanmuBean(videoUrl)

        val uri = Uri.parse(videoUrl)
        val fileHash = IOUtils.getFileHash(uri.path)
//        if (fileHash == null) {
//            loadDanmuLiveData.postValue(loadResult)
//            return
//        }

        loadResult.state = LoadDanmuState.MATCHING
        loadDanmuLiveData.postValue(loadResult)
//        val danmuInfo = DanmuUtils.matchDanmuSilence(videoUrl, fileHash)
        var danmuInfo: Pair<String, Int>? = null
        val oyydsSearchdata = getOyydsSearchData(videoUrl)
        if (oyydsSearchdata != null) {
            danmuInfo = DanmuUtils.matchDanmuSilenceOyyds(
                videoUrl,
                "",
                oyydsSearchdata.keyword,
                oyydsSearchdata.epNumber
            )
        } else {
//   项目原生调用方式
            danmuInfo = fileHash?.let { DanmuUtils.matchDanmuSilence(videoUrl, it) }
        }
        if (danmuInfo == null) {
            loadResult.state = LoadDanmuState.NO_MATCHED
            loadDanmuLiveData.postValue(loadResult)
            return
        }

        loadResult.state = LoadDanmuState.MATCH_SUCCESS
        loadResult.danmuPath = danmuInfo.first
        loadResult.episodeId = danmuInfo.second
        loadDanmuLiveData.postValue(loadResult)
    }

    private suspend fun loadNetworkDanmu(videoSource: BaseVideoSource) {

        val loadResult = LoadDanmuBean(videoSource.getVideoUrl())
        val headers = videoSource.getHttpHeader() ?: emptyMap()

        loadResult.state = LoadDanmuState.COLLECTING
        loadDanmuLiveData.postValue(loadResult)

        var hash: String? = null
        try {
            val response = Retrofit.extService.downloadResource(videoSource.getVideoUrl(), headers)
            hash = FileHashUtils.getHash(response.byteStream())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (hash.isNullOrEmpty()) {
            loadResult.state = LoadDanmuState.NOT_SUPPORTED
            loadDanmuLiveData.postValue(loadResult)
            return
        }

        loadResult.state = LoadDanmuState.MATCHING
        loadDanmuLiveData.postValue(loadResult)

        // var  danmuInfo = DanmuUtils.matchDanmuSilence(videoSource.getVideoTitle(), hash)
        var danmuInfo: Pair<String, Int>? = null
        val oyydsSearchdata = getOyydsSearchData(videoSource.getVideoTitle())
        if (oyydsSearchdata != null) {
            danmuInfo = DanmuUtils.matchDanmuSilenceOyyds(
                videoSource.getVideoTitle(),
                "",
                oyydsSearchdata.keyword,
                oyydsSearchdata.epNumber
            )
        } else {
            // 项目原生调用方式
            danmuInfo = DanmuUtils.matchDanmuSilence(videoSource.getVideoTitle(), hash)
        }
        if (danmuInfo == null) {
            loadResult.state = LoadDanmuState.NO_MATCHED
            loadDanmuLiveData.postValue(loadResult)
            return
        }

        loadResult.state = LoadDanmuState.MATCH_SUCCESS
        loadResult.danmuPath = danmuInfo.first
        loadResult.episodeId = danmuInfo.second
        loadDanmuLiveData.postValue(loadResult)
    }

    fun searchDanmu(searchText: String) {
        if (searchText.isEmpty())
            return

        httpRequest<List<DanmuSourceContentBean>>(viewModelScope) {

            api {
                val searchResult = Retrofit.service.searchDanmu(searchText, "")
                val animeData = searchResult.animes ?: mutableListOf()

                mapDanmuSourceData(animeData)
            }

            onSuccess {
                danmuSearchLiveData.postValue(it)
            }
        }
    }

    private fun mapDanmuSourceData(animeData: MutableList<DanmuAnimeData>): List<DanmuSourceContentBean> {
        val danmuData = mutableListOf<DanmuSourceContentBean>()

        animeData.sortedWith(FileNameComparator(
            getName = { it.animeTitle ?: "" },
            isDirectory = { false }
        )).forEach { anime ->
            val animeName = anime.animeTitle ?: return@forEach
            val episodes = anime.episodes ?: return@forEach

            val contentData = episodes.map {
                DanmuSourceContentBean(animeName, it.episodeTitle, it.episodeId)
            }
            danmuData.addAll(contentData)
        }

        return danmuData
    }

    fun downloadDanmu(contentBean: DanmuSourceContentBean) {
        httpRequest<Pair<String, Int>?>(viewModelScope) {
            onStart { showLoading() }

            api {
                val danmuData = Retrofit.service.getDanmuContent(
                    contentBean.episodeId.toString(),
                    true
                )
                val danmuFileName = contentBean.animeTitle + "_" + contentBean.episodeTitle + ".xml"
                val danmuPath = DanmuUtils.saveDanmu(danmuData, null, danmuFileName)


                if (danmuPath.isNullOrEmpty()) {
                    null
                } else {
                    Pair(danmuPath, contentBean.episodeId)
                }
            }

            onSuccess {
                downloadDanmuLiveData.postValue(it)
            }

            onError {
                downloadDanmuLiveData.postValue(null)
            }

            onComplete { hideLoading() }
        }
    }
}