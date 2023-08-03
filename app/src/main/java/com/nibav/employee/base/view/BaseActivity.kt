package com.nibav.employee.base.view

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.LocalOnlyHotspotCallback
import android.net.wifi.WifiManager.LocalOnlyHotspotReservation
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Calendar
import java.util.Date


abstract class BaseActivity : AppCompatActivity() {

    lateinit var mAdminComponentName: ComponentName
    lateinit var mDevicePolicyManager: DevicePolicyManager

    var name = ""
    var number = ""

    var wifiConf: WifiConfiguration? = null
    var softApConfiguration: SoftApConfiguration? = null

    private var mReservation: LocalOnlyHotspotReservation? = null

    fun turnOnHotspot(onHotspotOnCallback: (String, String) -> Unit) {
        if ((checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            || checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        ) {

            val manager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            if (mReservation != null) {
                getNetworkCredentials(mReservation, onHotspotOnCallback)
            } else {
                manager.startLocalOnlyHotspot(object : LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: LocalOnlyHotspotReservation) {
                        super.onStarted(reservation)

                        mReservation = reservation

                        getNetworkCredentials(mReservation, onHotspotOnCallback)
                    }

                    override fun onFailed(reason: Int) {
                        super.onFailed(reason)

                        Log.d("TAG", "onStarted: $reason")
                    }
                }, Handler(Looper.getMainLooper()))
            }
        }
    }

    private fun getNetworkCredentials(
        mReservation: LocalOnlyHotspotReservation?,
        onHotspotOnCallback: (String, String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            softApConfiguration = mReservation?.softApConfiguration
            onHotspotOnCallback.invoke(
                softApConfiguration?.wifiSsid.toString(),
                softApConfiguration?.passphrase.toString()
            )
        } else {
            wifiConf = mReservation?.wifiConfiguration
            onHotspotOnCallback.invoke(
                wifiConf?.SSID.toString(),
                wifiConf?.preSharedKey.toString()
            )
        }
    }

    fun turnOffHotspot() {
        if (mReservation != null) {
            mReservation?.close()
            mReservation = null
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(nw)
        return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH))
    }

    fun adjustBrightness(brightness: Float) {
        val lp = this.window.attributes
        lp.screenBrightness = brightness
        this.window.attributes = lp
    }

    fun getCallBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString("name", name)
        bundle.putString("number", number)
        return bundle
    }

    fun startBlinkAnimation(view: View, duration: Long) {
        val anim: Animation = AlphaAnimation(0.0f, 1.0f)
        anim.duration = duration //You can manage the blinking time with this parameter
        anim.startOffset = 20
        anim.repeatMode = Animation.REVERSE
        anim.repeatCount = Animation.INFINITE
        view.startAnimation(anim)
    }

    fun appendLog(text: String?) {
        val log = Date.from(Instant.now()).toString() + " " + text
        val logFile = getLogFilePath()
        if (logFile?.exists() == false) {
            try {
                logFile.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        try {
            //BufferedWriter for performance, true to set append to file flag
            val buf = BufferedWriter(FileWriter(logFile, true))
            buf.append(log)
            buf.newLine()
            buf.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun getLogFilePath(): File? {
        var directory: File?
        directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .toString() + "/Nibav/employee/logs"
        )
        if (!directory.exists()) {
            // Make it, if it doesn't exit
            val success = directory.mkdirs()
            if (!success) {
                directory = null
            }
        }

        val fileName = "log.txt"
        return File(directory, fileName)
    }

    fun getUpdateApkPath(fileName:String): File? {
        var directory: File?
        directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .toString() + "/Nibav/employee/$fileName"
        )
        if (!directory.exists()) {
            // Make it, if it doesn't exit
            val success = directory.mkdirs()
            if (!success) {
                directory = null
            }
        }
        return directory
    }

    fun openInstallerToWhitelist() {
        val installerPackage = "com.nibav.installer"
        if (isAppInstalled(installerPackage)) {
            val i = packageManager.getLaunchIntentForPackage(installerPackage)
            i?.putExtra("isUpdate", false)
            startActivity(i)
            finishAffinity()
        }
    }

    fun isAppInstallerAvailable(): Boolean {
        val installerPackage = "com.nibav.installer"
        if (isAppInstalled(installerPackage)) {
            return true
        }
        return false
    }

    fun isWhatsAppInstalled(uri: String): Boolean {
        val pm = packageManager
        try {
            pm.getPackageInfo(uri, PackageManager.GET_META_DATA)
            return true
        } catch (e: PackageManager.NameNotFoundException) {
        }
        return false
    }

    fun isAppInstalled(uri: String): Boolean {
        val pm = packageManager
        try {
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES)
            return true
        } catch (e: PackageManager.NameNotFoundException) {
        }
        return false
    }

    fun tvAppend(view: TextView, s: String, text: ByteArray) {
        val strData = "\n${text.contentToString()}"
        runOnUiThread {
            if (view.visibility == View.VISIBLE && view.layout != null) {
                clearTextIfNeeded(view)

                view.append("$s $strData\n")
                val scrollAmount: Int = view.layout
                    .getLineTop(view.lineCount) - view.height

                if (scrollAmount > 0) view.scrollTo(0, scrollAmount)
                else view.scrollTo(0, 0)
            }
        }
    }

    fun tvAppend(view: TextView, s: String) {
        runOnUiThread {
            if (view.visibility == View.VISIBLE && view.layout != null) {
                clearTextIfNeeded(view)

                view.append("$s\n")
                val scrollAmount: Int = view.layout
                    .getLineTop(view.lineCount) - view.height

                if (scrollAmount > 0) view.scrollTo(0, scrollAmount)
                else view.scrollTo(0, 0)
            }
        }
    }

    private var i = 0
    private fun clearTextIfNeeded(view: TextView) {
        i++
        if (i == 50) view.text = ""
    }


    fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    protected fun showInFullscreen(view: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowController = WindowInsetsControllerCompat(window, view)
        windowController.hide(WindowInsetsCompat.Type.systemBars())
        windowController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /*fun hasCameraPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this,
            Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }*/

    fun hasCallPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun handleRequiredPermissions(isAllPermissionsGranted: (Boolean) -> Unit) {
        val permissions = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            // Manifest.permission.CAMERA,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        Dexter.withContext(this)
            .withPermissions(permissions)
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(multiplePermissionsReport: MultiplePermissionsReport) {

                    val deniedPermissions = mutableListOf<String>()
                    for (p in multiplePermissionsReport.deniedPermissionResponses)
                        deniedPermissions.add(p.permissionName)

                    if (multiplePermissionsReport.areAllPermissionsGranted() || deniedPermissions.contains(
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                        || deniedPermissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)
                    ) {
                        showLauncherSelection()

                        isAllPermissionsGranted.invoke(true)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            if (!Environment.isExternalStorageManager()) requestManageStoragePermissions()

                        requestOverLayPermission()

                        if (!packageManager.canRequestPackageInstalls()) {
                            requestInstallPackagesResult.launch(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                    .setData(Uri.parse(String.format("package:%s", packageName)))
                            )
                        } else requestWriteSettingsPermissions()
                    } else {
                        isAllPermissionsGranted.invoke(false)
                        finishAffinity()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    list: List<PermissionRequest>,
                    permissionToken: PermissionToken
                ) = Unit
            }).check()
    }

    private val startForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            Log.d("NIBAV_home_permission", "granted")
        }
    }

    private fun showLauncherSelection() {
        val roleManager = getSystemService(Context.ROLE_SERVICE)
                as RoleManager
        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        ) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            startForResult.launch(intent)
        }
    }

    fun requestManageStoragePermissions() {
        val intent = Intent()
        intent.action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun requestOverLayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + this.packageName)
                )
                startActivity(intent)
            } else {
                //Permission Granted-System will work
            }
        }
    }

    private val requestInstallPackagesResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        requestWriteSettingsPermissions()
    }

    private fun requestWriteSettingsPermissions() {
        if (!Settings.System.canWrite(this@BaseActivity)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    fun placeCall(name: String, number: String) {
        disableKioskMode()
        this.name = name
        this.number = number
        makeCall(number)
        enableKioskMode()
    }

    fun getCurrentDateTime(): String {
        return SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z").format(Calendar.getInstance().time)
    }

    fun placeCall(number: String) {
        disableKioskMode()
        this.number = number
        this.name = ""
        makeCall(number)
        enableKioskMode()
    }

    private fun makeCall(number: String) {
        if (number.endsWith("#")) {
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = ussdToCallableUri(number)
            try {
                startActivity(intent)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        } else {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                putExtra(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true)
            }
            startActivity(intent)
        }
    }

    private fun ussdToCallableUri(ussd: String): Uri? {
        var uriString: String? = ""
        if (!ussd.startsWith("tel:")) uriString += "tel:"
        for (c in ussd.toCharArray()) {
            if (c == '#') uriString += Uri.encode("#") else uriString += c
        }
        return Uri.parse(uriString)
    }

    fun disableKioskMode() {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        if (mDevicePolicyManager.isLockTaskPermitted(packageName)) {
            if (manager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED)
                stopLockTask()
        } else openInstallerToWhitelist()
    }

    fun enableKioskMode() {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        if (manager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            if (mDevicePolicyManager.isLockTaskPermitted(packageName)) {
                startLockTask()
            } else openInstallerToWhitelist()
        }
    }
}