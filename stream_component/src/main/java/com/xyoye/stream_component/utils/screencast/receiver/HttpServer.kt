package com.xyoye.stream_component.utils.screencast.receiver

import android.util.Base64
import com.xyoye.common_component.utils.EntropyUtils
import com.xyoye.common_component.utils.JsonHelper
import com.xyoye.data_component.data.CommonJsonData
import fi.iki.elonen.NanoHTTPD
import kotlin.random.Random

/**
 * <pre>
 *     author: xyoye1997@outlook.com
 *     time  : 2022/7/21
 *     desc  : 投屏内容接收方（TV）HTTP服务器
 * </pre>
 */

class HttpServer(private val password: String?) : NanoHTTPD(randomPort()) {

    companion object {
        //随机端口
        private fun randomPort() = Random.nextInt(20000, 30000)
    }

    override fun serve(session: IHTTPSession?): Response {
        if (session != null && session.method == Method.GET) {
            //身份验证
            if (authentication(session).not()) {
                return unauthorizedResponse()
            }

            //处理请求
            val response = ServerController.handleGetRequest(session.uri, session.parameters)
            if (response != null) {
                return response
            }
        }
        return super.serve(session)
    }

    private fun authentication(session: IHTTPSession): Boolean {
        if (password.isNullOrEmpty()) {
            return true
        }

        var authorization = session.headers["authorization"]
        if (authorization == null) {
            authorization = session.headers["Authorization"]
        }
        if (authorization.isNullOrEmpty()) {
            return false
        }
        if (authorization.startsWith("Bearer ").not()) {
            return false
        }
        try {
            return password == EntropyUtils.aesDecode(
                UdpServer.multicastMsgKey,
                authorization.substring("Bearer ".length),
                Base64.NO_WRAP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun unauthorizedResponse(): Response {
        val jsonData = CommonJsonData(
            errorCode = 401,
            success = false,
            errorMessage = "连接验证失败"
        )
        val json = JsonHelper.toJson(jsonData)
        return newFixedLengthResponse(json)
    }
}