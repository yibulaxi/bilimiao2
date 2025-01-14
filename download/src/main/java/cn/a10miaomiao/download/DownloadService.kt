package cn.a10miaomiao.download

import android.app.Service
import android.content.Intent
import android.os.*
import android.os.Environment.*
import android.util.Log
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.InputStream
import java.lang.Exception

class DownloadService : Service(), DownloadManager.Callback {
    private val TAG = this::class.simpleName

    private lateinit var downloadBinder: DownloadBinder
    private val downloadManager = DownloadManager().apply {
        callback = this@DownloadService
    }

    private var downloadList = arrayListOf<BiliVideoEntry>()
    private var curDownload: BiliVideoEntry? = null

    var getPlayUrl: ((entry: BiliVideoEntry) -> BiliVideoPlayUrlEntry)? = null
    var getDanmakuXML: ((cid: String) -> InputStream)? = null
    var downloadCallback: ((entry: BiliVideoEntry, download: DownloadInfo) -> Unit)? = null

    override fun onBind(intent: Intent) = downloadBinder

    override fun onCreate() {
        super.onCreate()
        downloadBinder = DownloadBinder(this)
    }

    fun getDownloadPath(): String {
        var file = File(getExternalFilesDir(null), "../download")
        if (!file.exists()) {
            file.mkdir()
        }
        return file.canonicalPath
    }

    fun getDownloadList(): ArrayList<BiliVideoEntry> {
        if (downloadList.size === 0) {
            val downloadDir = File(getDownloadPath())
            downloadDir.listFiles()
                    .filter { it.isDirectory }
                    .map { it.listFiles().filter { pageDir -> pageDir.isDirectory } }
                    .forEach { pageDir ->
                        pageDir.forEach {
                            // 如果是文件夹，判断里面有没有entry.json
                            if (it.isDirectory) {
                                val entryJsonFile = File(it.path, "entry.json")
                                if (entryJsonFile.exists()) {
                                    val entryJson = FileUtil.readTxtFile(entryJsonFile)
                                    val entry = Gson().fromJson(entryJson, BiliVideoEntry::class.java)
                                    downloadList.add(entry)
                                }
                            }
                        }
                    }
        }
        return downloadList
    }

    /**
     * 创建下载
     */
    fun createDownload(
            biliVideo: BiliVideoEntry
    ) {
        val pageDir = getDownloadFileDir(biliVideo)
        // 保存视频信息
        val entryJsonFile = File(pageDir, "entry.json")
        val entryJsonStr = Gson().toJson(biliVideo)
        FileUtil.writeTxtFile(entryJsonFile, entryJsonStr, false)
        downloadList.add(biliVideo)
        if (curDownload == null) {
            startDownload(biliVideo)
        }
    }

    /**
     * 开始任务
     */
    fun startDownload(biliVideo: BiliVideoEntry) {
        // 取消当前任务
        downloadManager.cancel()
        curDownload = biliVideo
        // 开始任务/继续任务
        val pageDir = getDownloadFileDir(biliVideo)
        val danmakuXMLFile = File(pageDir, "danmaku.xml")
        if (!danmakuXMLFile.exists()) {
            // 获取弹幕并下载
            postDownloadInfo(DownloadInfo(
                key = biliVideo.page_data.cid.toString(),
                name = biliVideo.title + biliVideo.page_data.part,
                url = "",
                status = 101
            ))
            Observable.create<InputStream> {
                Log.d("RxJava2", "This msg from work thread :" + Thread.currentThread().getName());
                val inputStream = getDanmakuXML?.invoke(biliVideo.page_data.cid.toString())!!
                it.onNext(inputStream)
                it.onComplete()
            }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe({
                val danmakuXMLFile = File(pageDir, "danmaku.xml")
                FileUtil.inputStreamToFile(it, danmakuXMLFile)
                downloadVideo(biliVideo)
            }, {
                postDownloadInfo(DownloadInfo(
                        key = biliVideo.page_data.cid.toString(),
                        name = biliVideo.title + biliVideo.page_data.part,
                        url = "",
                        status = -101
                ))
                //TODO: 下载弹幕失败后续处理
                it.printStackTrace()
            })
        } else {
            downloadVideo(biliVideo)
        }
    }

    fun downloadVideo (biliVideo: BiliVideoEntry) {
        val pageDir = getDownloadFileDir(biliVideo)
        val videoDir = File(pageDir, biliVideo.type_tag)
        if (!videoDir.exists()) {
            videoDir.mkdir()
        }
        postDownloadInfo(
            DownloadInfo(
                key = biliVideo.page_data.cid.toString(),
                name = biliVideo.title + biliVideo.page_data.part,
                url = "",
                status = 102
            )
        )
        // 获取播放地址并下载
        Observable.create<BiliVideoPlayUrlEntry> {
            val playUrl = getPlayUrl?.invoke(biliVideo)!!
            it.onNext(playUrl)
            it.onComplete()
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe({
                val videoJsonFile = File(videoDir, "index.json")
                val videoJsonStr = Gson().toJson(it)
                FileUtil.writeTxtFile(videoJsonFile, videoJsonStr, false)
                downloadManager.cancel()
                val info = DownloadInfo(
                        key = biliVideo.page_data.cid.toString(),
                        name = biliVideo.title + biliVideo.page_data.part,
                        url = it.segment_list[0].url,
                        header = mapOf(
                                "Referer" to "https://www.bilibili.com/video/av${biliVideo.avid}",
                                "User-Agent" to it.user_agent
                        ),
                        size = it.segment_list[0].bytes,
                        length = it.segment_list[0].duration
                )
                downloadManager.start(
                        info,
                        File(
                                videoDir, "0" + "." + it.format
                        )
                )
                postDownloadInfo(info)
            }, {
               postDownloadInfo(DownloadInfo(
                        key = biliVideo.page_data.cid.toString(),
                        name = biliVideo.title + biliVideo.page_data.part,
                        url = "",
                        status = -102
                ))
                //TODO: 获取播放地址失败后续处理
                it.printStackTrace()
            })
    }

    /**
     * 结束当前任务
     */
    fun stopDownload () {
        curDownload?.let { biliVideo ->
            val pageDir = getDownloadFileDir(biliVideo)
            downloadList.forEach {
                if (biliVideo.avid == it.avid && biliVideo.page_data.cid == it.page_data.cid) {
                    it.total_bytes = biliVideo.total_bytes
                    it.downloaded_bytes = biliVideo.downloaded_bytes
                }
            }
            // 保存视频信息，更新进度
            val entryJsonFile = File(pageDir, "entry.json")
            val entryJsonStr = Gson().toJson(biliVideo)
            FileUtil.writeTxtFile(entryJsonFile, entryJsonStr, false)
            downloadManager.cancel()?.let { info ->
                postDownloadInfo(info)
            }
            curDownload = null
        }
    }

    fun delectDownload (biliVideo: BiliVideoEntry) {
        curDownload?.let {
            if (biliVideo.avid == it.avid && biliVideo.page_data.cid == it.page_data.cid) {
                stopDownload()
            }
        }
        val downloadDir = File(getDownloadPath(), biliVideo.avid.toString())
        if (downloadDir.exists()) {
            val pageDir = File(downloadDir, biliVideo.page_data.page.toString())
            if (pageDir.exists()) {
                pageDir.deleteRecursively()
            }
            if (downloadDir.listFiles().size === 0) {
                downloadDir.delete()
            }
        }
        val index = downloadList.indexOfFirst {
            biliVideo.avid == it.avid && biliVideo.page_data.cid == it.page_data.cid
        }
        downloadList.removeAt(index)
    }

    private fun nextDownload() {
        downloadList.find {
            it.downloaded_bytes == 0L
        }?.let {
            startDownload(it)
        }
    }

    private fun getDownloadFileDir(biliVideo: BiliVideoEntry): File {
        val downloadDir = File(getDownloadPath(), biliVideo.avid.toString())
        // 创建文件夹
        if (!downloadDir.exists()) {
            downloadDir.mkdir()
        }
        val pageDir = File(downloadDir, biliVideo.page_data.page.toString())
        if (!pageDir.exists()) {
            pageDir.mkdir()
        }
        return pageDir
    }

    override fun onTaskRunning(info: DownloadInfo) {
        curDownload?.let {
            it.total_bytes = info.size
            it.downloaded_bytes = info.progress
            postDownloadInfo(info)
        }
//
//        Log.d(TAG, info.progress.toString())
    }

    override fun onTaskComplete(info: DownloadInfo) {
        curDownload?.let { biliVideo ->
            biliVideo.total_bytes = info.size
            biliVideo.downloaded_bytes = info.progress
            val pageDir = getDownloadFileDir(biliVideo)
            downloadList.forEach {
                if (biliVideo.avid == it.avid && biliVideo.page_data.cid == it.page_data.cid) {
                    biliVideo.is_completed = true
                    it.is_completed = true
                }
            }
            // 更新视频信息
            val entryJsonFile = File(pageDir, "entry.json")
            val entryJsonStr = Gson().toJson(biliVideo)
            FileUtil.writeTxtFile(entryJsonFile, entryJsonStr, false)
            postDownloadInfo(info)
        }
        curDownload = null
        nextDownload()
    }

    override fun onTaskError(info: DownloadInfo, error: Throwable) {
        error.printStackTrace()
        curDownload?.let {
            it.total_bytes = info.size
            it.downloaded_bytes = info.progress
            postDownloadInfo(info)
        }
        curDownload = null
    }

    fun postDownloadInfo (info: DownloadInfo) {
        curDownload?.let {
            downloadCallback?.invoke(it, info)
        }
    }
}