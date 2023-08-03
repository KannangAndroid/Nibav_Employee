package com.nibav.employee

import android.Manifest
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.telecom.TelecomManager
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.nibav.employee.base.view.BaseActivity
import com.nibav.employee.databinding.ActivityMainBinding
import com.nibav.employee.service.DeviceAdmin
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

class MainActivity : BaseActivity() {


    private lateinit var binding: ActivityMainBinding
    private val PROVIDER_PATH = "com.nibav.employee.provider"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setAppAsAdmin()
        clickListener()
    }

    private fun clickListener() {
        binding.ivGmail.setOnClickListener {
           /* val intent = packageManager.getLaunchIntentForPackage("com.google.android.gm")
            startActivity(intent)*/
            if (hasCallPermissions()) {
                val manager =
                    getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                if (!(ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ANSWER_PHONE_CALLS
                    ) != PackageManager.PERMISSION_GRANTED
                )) {
                    manager.acceptRingingCall()
                }

            }
        }

        binding.ivMessage.setOnClickListener {
            val intent =
                packageManager.getLaunchIntentForPackage("com.nibav.employee.debug")
            startActivity(intent)
        }

        binding.ivWhatsapp.setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
            startActivity(intent)
        }

        binding.ivWhiteList.setOnClickListener {
            setLockTaskPackage("com.nibav.employee.debug")
        }

        binding.ivUpdate.setOnClickListener {
            installApp("salesforce.apk", "com.salesforce.chatter")
        }
    }

    private fun setAppAsAdmin() {
        mDevicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        mAdminComponentName = ComponentName(this, DeviceAdmin::class.java)

        if (!mDevicePolicyManager.isAdminActive(mAdminComponentName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mAdminComponentName)
            adminAccessResult.launch(intent)
        } else {
            listenToPermissionResponse()
        }
    }

    private fun listenToPermissionResponse() {
        handleRequiredPermissions {
            // viewModel.isAllPermissionsGranted.postValue(it)
        }
    }


    private val adminAccessResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            listenToPermissionResponse()
        } else finishAffinity()
    }

    override fun onResume() {
        super.onResume()
        enableKioskMode()
    }

    private fun setLockTaskPackage(packageName: String) {
        val installerPackage = "com.nibav.installer"
        if (isAppInstalled(installerPackage)) {
            val i = packageManager.getLaunchIntentForPackage(installerPackage)
            i?.putExtra("isUpdate", false)
            i?.putExtra("isNewPackage", true)
            i?.putExtra("newPackage", packageName)
            startActivity(i)
            finishAffinity()
        }
    }

    private fun installApp(fileName: String, packageName: String) {
        if (isFileAvailable(fileName)) {
            val path = getUpdateApkPath(fileName)?.path.toString()
            showInstallOption(path)
            // upgrade(path, fileName, packageName)
        }
    }

    private fun upgrade(path: String, apkFileName: String, packageName: String) {
        Thread {
            val packageManger = packageManager
            val packageInstaller = packageManger.packageInstaller
            val params = SessionParams(
                SessionParams.MODE_FULL_INSTALL
            )
            params.setAppPackageName(packageName)
            try {
                val file = File(path)
                Log.d("TAG", "upgrade: " + file.path)
                val ins: InputStream = FileInputStream(file)
                val sessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(sessionId)
                val out = session.openWrite(apkFileName, 0, -1)
                val bufsize = 4096
                val bytes = ByteArray(bufsize)
                var len = 1 // any value > 0
                while (len > 0) {
                    len = ins.read(bytes, 0, bufsize)
                    if (len < 1) break
                    out.write(bytes, 0, len)
                }
                ins.close()
                session.fsync(out)
                out.close()
                Log.d("TAG", "about to commit ")
                session.commit(
                    PendingIntent.getBroadcast(
                        this@MainActivity, sessionId,
                        Intent("com.salesforce.chatter.intent.INSTALL"), PendingIntent.FLAG_MUTABLE
                    ).intentSender
                )
                Log.d("TAG", "committed ")
            } catch (e: IOException) {
                showToast("Error installing package $apkFileName")
                Log.d("TAG", "Error installing package $apkFileName", e)
            } finally {
                showToast("Updated")
            }
        }.start()
    }

    private fun getDirectory(): File? {
        var directory: File?
        directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .toString() + "/Nibav/employee/"
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


    private fun isFileAvailable(apkName: String): Boolean {
        val path = getDirectory()
        val outputFile = File(path, "$apkName")
        if (outputFile.exists()) {
            return true
        }
        return false
    }


    private fun showInstallOption(
        destination: String
    ) {
        val contentUri = FileProvider.getUriForFile(
            this,
            PROVIDER_PATH,
            File(destination)
        )
        val install = Intent(Intent.ACTION_VIEW)
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        install.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        install.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        install.data = contentUri
        startActivity(install)
        // finish()
    }

}