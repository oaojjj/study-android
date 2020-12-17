import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.MediaController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {
    companion object {
        private const val VIDEO_REQUEST = 1000

        // content provider
        private val CONTENT_URI_TYPE = listOf(
            "com.android.externalstorage.documents",
            "com.android.providers.downloads.documents",
            "com.android.providers.media.documents"
        )
    }

    // 동영상의 현재 포지션을 저장하는 변수
    private var currentPosition = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        showPermissionDialog()

        bt_load_video.setOnClickListener {
            selectVideoFromGallery()
        }

        video_container.setMediaController(MediaController(this)) // 컨트롤러 연결
        video_container.setOnPreparedListener {
            MediaPlayer.OnPreparedListener {
                video_container.start() // 비디오 설정이 다끝나면 재생
            }
        }
    }

    // 갤러리에 있는 비디오 URI 가져오기
    private fun selectVideoFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "video/*"

        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(intent, VIDEO_REQUEST)
    }

    // URI 타입, Provider 타입별로 경로따기
    private fun getPath(context: Context, uri: Uri): String? {
        when {
            DocumentsContract.isDocumentUri(context, uri) -> {
                when (uri.authority) {
                    CONTENT_URI_TYPE[0] -> {
                        val docId = DocumentsContract.getDocumentId(uri)
                        val split = docId.split(":").toTypedArray()
                        val type = split[0]
                        if ("primary".equals(type, ignoreCase = true)) {
                            return Environment.getExternalStorageDirectory()
                                .toString() + "/" + split[1]
                        }
                    }
                    CONTENT_URI_TYPE[1] -> {
                        val id = DocumentsContract.getDocumentId(uri)
                        val contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"),
                            java.lang.Long.valueOf(id)
                        )
                        return getDataColumn(context, contentUri, null, null)
                    }
                    CONTENT_URI_TYPE[2] -> {
                        val docId = DocumentsContract.getDocumentId(uri)
                        val split = docId.split(":").toTypedArray()
                        val contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        val selection = "_id=?"
                        val selectionArgs = arrayOf(split[1])
                        return getDataColumn(context, contentUri, selection, selectionArgs)
                    }
                }
            }
            "content".equals(uri.scheme, ignoreCase = true) -> {
                return getDataColumn(context, uri, null, null)
            }
            "file".equals(uri.scheme, ignoreCase = true) -> {
                return uri.path
            }
        }
        return null
    }

    // 실제 경로에 있는 데이터 찾아서 return
    private fun getDataColumn(
        context: Context, uri: Uri?, selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(
            column
        )
        try {
            cursor = context.contentResolver.query(
                uri!!, projection, selection, selectionArgs,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex: Int = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(columnIndex)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    // 비디오 선택하고 받는 결과 인텐트
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && requestCode == VIDEO_REQUEST) {
            val uri = data?.data

            if (uri != null) {
                val videoPath = getPath(applicationContext, uri)
                video_container.setVideoPath(videoPath)
            }
            Log.d("onActivityResult", uri.toString())
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    // 영상의 포지션을 기억했다가 앱이 resume 되면 영상을 그시점에서 실행
    override fun onResume() {
        super.onResume()
        if (!video_container.isPlaying) {
            video_container.resume()
            video_container.seekTo(currentPosition)
        }
    }

    // 앱이 pause 상태가 되면 현재 영상의 포지션을 기억
    override fun onPause() {
        super.onPause()

        if (video_container != null && video_container.isPlaying) {
            video_container.pause()
            currentPosition = video_container.currentPosition
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        video_container?.stopPlayback()
    }

    private fun checkPermission(): Int {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    // 권한 체크
    private fun showPermissionDialog() {
        when (checkPermission()) {
            PackageManager.PERMISSION_GRANTED -> {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
            PackageManager.PERMISSION_DENIED -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    100
                )
            }

        }
    }

    // 권한 체크 콜백메소드
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            100 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "권한이 승인 되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            else -> Toast.makeText(this, "권한이 거부 되었습니다.", Toast.LENGTH_SHORT).show()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
