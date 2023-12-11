package com.xyoye.common_component.utils

import android.util.Log
import com.xyoye.common_component.extension.formatFileName
import com.xyoye.common_component.network.Retrofit
import com.xyoye.data_component.data.DanmuContentData
import com.xyoye.data_component.data.DanmuData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.helpers.AttributesImpl
import java.io.*
import javax.xml.transform.OutputKeys
import javax.xml.transform.sax.SAXTransformerFactory
import javax.xml.transform.stream.StreamResult

/**
 * Created by xyoye on 2020/11/23.
 */

object DanmuUtils {

    fun saveDanmu(fileName: String, inputStream: InputStream, directoryName: String? = null): String? {
        val directory = if (directoryName != null && directoryName.isNotEmpty()) {
            val directory = File(PathHelper.getDanmuDirectory(), directoryName)
            if (directory.exists().not()) {
                directory.mkdirs()
            }
            directory
        } else {
            PathHelper.getDanmuDirectory()
        }

        val danmuFile = File(directory, fileName.formatFileName())
        if (danmuFile.exists()) {
            danmuFile.delete()
        }

        var outputStream: OutputStream? = null
        try {
            danmuFile.createNewFile()
            outputStream = BufferedOutputStream(FileOutputStream(danmuFile, false))
            val data = ByteArray(512 * 1024)
            var len: Int
            while (inputStream.read(data).also { len = it } != -1) {
                outputStream.write(data, 0, len)
            }
            outputStream.flush()
            return danmuFile.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        } finally {
            IOUtils.closeIO(inputStream)
            IOUtils.closeIO(outputStream)
        }
    }

    fun saveDanmu(fileName: String, folderPath: String?, xmlContent: String): String? {
        val danmuDir = if (folderPath == null)
            PathHelper.getDanmuDirectory()
        else
            File(PathHelper.getDanmuDirectory(), folderPath)
        if (!danmuDir.exists()) {
            danmuDir.mkdirs()
        }

        val danmuFile = File(danmuDir, fileName.formatFileName())
        if (danmuFile.exists()) {
            danmuFile.delete()
        }

        var fileOutputStream: FileOutputStream? = null
        var bufferedWriter: BufferedWriter? = null
        return try {
            danmuFile.createNewFile()
            fileOutputStream = FileOutputStream(danmuFile, false)
            bufferedWriter = BufferedWriter(OutputStreamWriter(fileOutputStream))
            bufferedWriter.write(xmlContent)
            bufferedWriter.newLine()
            bufferedWriter.flush()
            danmuFile.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } finally {
            IOUtils.closeIO(fileOutputStream)
            IOUtils.closeIO(bufferedWriter)
        }
    }

    fun saveDanmu(danmuData: DanmuData, folderPath: String?, fileName: String): String? {
        val danmuDir = if (folderPath.isNullOrEmpty())
            PathHelper.getDanmuDirectory()
        else
            File(PathHelper.getDanmuDirectory(), folderPath)
        if (!danmuDir.exists()) {
            danmuDir.mkdirs()
        }
        val danmuFile = File(danmuDir, fileNameFormat(fileName))
        if (danmuFile.exists())
            danmuFile.delete()
        danmuFile.createNewFile()

        val danmuContent = buildXmlContent(danmuData) ?: return null

        var fileWriter: FileWriter? = null
        var bufferedWriter: BufferedWriter? = null
        try {
            fileWriter = FileWriter(danmuFile, false)
            bufferedWriter = BufferedWriter(fileWriter)
            bufferedWriter.write(danmuContent)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            IOUtils.closeIO(bufferedWriter)
            IOUtils.closeIO(fileWriter)
        }

        return danmuFile.absolutePath
    }

    private fun fileNameFormat(fileName: String): String {
        val formatted = fileName.formatFileName()
        // 文件名超过50个字符，截断
        if (formatted.length > 50) {
            val substringName = formatted.removeSuffix(".xml").substring(0, 50)
            return "$substringName.xml"
        }
        return formatted
    }

    private fun buildXmlContent(danmuData: DanmuData): String? {
        var danmuContent: String? = null
        try {
            val xmlWriter = StringWriter()
            val factory = SAXTransformerFactory.newInstance() as SAXTransformerFactory
            val handler = factory.newTransformerHandler()

            handler.transformer.apply {
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty(OutputKeys.ENCODING, "utf-8")
                setOutputProperty(OutputKeys.VERSION, "1.0")
            }

            val result = StreamResult(xmlWriter)
            handler.setResult(result)

            handler.startDocument()
            val attr = AttributesImpl()
            attr.clear()
            handler.startElement("", "", "i", attr)

            for (comment in danmuData.comments) {
                val paramText = comment.p
                val commentText = comment.m
                if (paramText == null || commentText == null)
                    continue

                attr.clear()
                val params = paramText.split(",")
                val stringBuilder = StringBuilder()
                for (i in params.indices) {
                    if (i == 2) {
                        //颜色值为0时，默认设置为白色
                        if (params[i] == "0" || params[i] == "-1") {
                            stringBuilder.append("16777215").append(",")
                            continue
                        }
                    }
                    stringBuilder.append(params[i]).append(",")
                    if (i == 1) {
                        stringBuilder.append("25,")
                    }
                }
                val newParams =
                    stringBuilder.toString().substring(0, stringBuilder.length - 1)
                val attribute = "$newParams,0,0,0"
                attr.addAttribute("", "", "p", "", attribute)
                handler.startElement("", "", "d", attr)
                handler.characters(commentText.toCharArray(), 0, commentText.length)
                handler.endElement("", "", "d")
            }
            handler.endElement("", "", "i")
            handler.endDocument()
            danmuContent = xmlWriter.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return danmuContent
    }

    fun appendDanmu(danmuPath: String, appendText: String) {
        val danmuFile = File(danmuPath)
        if (!danmuFile.exists())
            return

        val tempFileName = getFileNameNoExtension(danmuPath)
        val tempFileExtension = getFileExtension(danmuFile)
        val tempFile = File(danmuFile.parentFile!!, tempFileName + "_temp.$tempFileExtension")

        var fileReader: FileReader? = null
        var bufferFileReader: BufferedReader? = null

        var fileOutputStream: FileOutputStream? = null
        var bufferedFileWriter: BufferedWriter? = null

        try {
            fileReader = FileReader(danmuFile)
            bufferFileReader = BufferedReader(fileReader)

            fileOutputStream = FileOutputStream(tempFile, false)
            bufferedFileWriter =
                BufferedWriter(OutputStreamWriter(fileOutputStream, Charsets.UTF_8))

            var danmuText: String? = bufferFileReader.readLine()
            while (danmuText != null) {
                if (danmuText == "</i>") {
                    bufferedFileWriter.write(appendText)
                    bufferedFileWriter.newLine()
                    bufferedFileWriter.write(danmuText)
                    bufferedFileWriter.newLine()
                    break
                }

                bufferedFileWriter.write(danmuText)
                bufferedFileWriter.newLine()

                danmuText = bufferFileReader.readLine()
            }
            bufferedFileWriter.flush()

            IOUtils.closeIO(bufferFileReader)
            IOUtils.closeIO(fileReader)

            tempFile.renameTo(danmuFile)
        } catch (e: Throwable) {
            DDLog.i("写入弹幕失败", e)
        } finally {
            IOUtils.closeIO(fileOutputStream)
            IOUtils.closeIO(bufferedFileWriter)
            IOUtils.closeIO(bufferFileReader)
            IOUtils.closeIO(fileReader)
        }
    }

    /**
     * 静默匹配弹幕
     */
    suspend fun matchDanmuSilence(filePath: String, fileHash: String): Pair<String, Int>? {
        return withContext(Dispatchers.IO) {
            try {
                //提取视频信息
                val params = HashMap<String, String>()

                params["fileName"] = getFileName(filePath)
                params["fileHash"] = fileHash
                params["fileSize"] = "0"
                params["videoDuration"] = "0"
                params["matchMode"] = "hashOnly"

                //匹配弹幕
                val danmuMatchData = Retrofit.service.matchDanmu(params)

                //只存在一个匹配的弹幕
                if (danmuMatchData.isMatched && danmuMatchData.matches!!.size == 1) {
                    val episodeId = danmuMatchData.matches!![0].episodeId
                    val danmuData = Retrofit.service.getDanmuContent(episodeId.toString(), true)

                    val folderName = getParentFolderName(filePath)
                    val fileNameNotExt = getFileNameNoExtension(filePath)
                    //保存弹幕, 返回路径
                    val danmuPath = saveDanmu(danmuData, folderName, "$fileNameNotExt.xml")
                    if (danmuPath != null) {
                        return@withContext Pair(danmuPath, episodeId)
                    }
                }
                return@withContext null
            } catch (e: Exception) {
                DDLog.e("自动匹配弹幕失败", e)
                return@withContext null
            }
        }
    }


    fun hexToColor(hex: String): Int {
        // 移除可能包含的 # 前缀
        val cleanHex = if (hex.startsWith("#")) hex.substring(1) else hex

        // 将16进制字符串转换为整数
        val colorValue = cleanHex.toLongOrNull(16)

        // 如果转换成功，返回32位整数，否则返回默认颜色（这里是白色）
        return colorValue?.toInt() ?: 0xFFFFFFFF.toInt()
    }

    /**
     * 静默匹配弹幕(oyyds)
     */
    suspend fun matchDanmuSilenceOyyds(filePath: String, fileHash: String,keywords:String="",epNumber:String="0"):  Pair<String, Int>? {
        return withContext(Dispatchers.IO) {
            try {
                //提取视频信息
                val params = HashMap<String, String>()

                params["fileName"] = getFileName(filePath)
                params["fileHash"] = fileHash
                params["fileSize"] = "0"
                params["videoDuration"] = "0"
                params["matchMode"] = "hashOnly"

                Log.d("oyyds params:",params.toString())
                val episodeId=0
                val epNumber=epNumber
                val oyydsSearchRes = Retrofit.oyydsService.searchDanmu(keywords,epNumber )
//                val total = oyydsSearchRes.data.total
                val datas=oyydsSearchRes.data.data;

//                Log.d("oyyds 搜索：",datas.toString())
                if (datas !== null && datas.isNotEmpty() ) {
                    val count= oyydsSearchRes.data.total
                    val comments= oyydsSearchRes.data.data!!.map {
                        val arr=it.split(',')
                        val cid=0
                        //p参数格式为出现时间,模式,颜色,用户ID，各个参数之间使用英文逗号分隔
                        val p1=arr[0]
                        val p2= if (arr[1] == "top") 5 else 1
                        val p3= hexToColor(arr[2])
                        val p="$p1,$p2,$p3,0"
                        val m=arr[3]
                        DanmuContentData(cid,p,m)
                    }.toMutableList()
                    val danmuData = DanmuData(count,comments)

//                    Log.d("oyyds item",danmuData.toString())
                    val folderName = getParentFolderName(filePath)
                    val fileNameNotExt = getFileNameNoExtension(filePath)
                    //保存弹幕, 返回路径
                    val danmuPath = saveDanmu(danmuData, folderName, "$fileNameNotExt.xml")
                    if (danmuPath != null) {
                        return@withContext Pair(danmuPath, episodeId)
                    }
                }


                return@withContext null
            } catch (e: Exception) {
                DDLog.e("自动匹配弹幕失败", e)
                return@withContext null
            }
        }
    }
}