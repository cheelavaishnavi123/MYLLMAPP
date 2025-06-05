package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object ExcelUtil {
    private const val CHANNEL_ID = "excel_download_channel"
    private const val NOTIFICATION_ID = 1001

    // Create notification channel
    private fun createNotificationChannel(context: Context) {
        val name = "Excel Download"
        val descriptionText = "Notifications for Excel file downloads"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    // Show notification
    private fun showNotification(context: Context, fileName: String) {
        createNotificationChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Added small icon
            .setContentTitle("Excel Downloaded")
            .setContentText("File '$fileName' saved to Downloads folder")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(NOTIFICATION_ID, builder.build())
            } catch (e: SecurityException) {
                Log.e("ExcelUtil", "Notification permission not granted", e)
            }
        }
    }

    fun createExcel(context: Context, fileName: String, data: List<Map<String, String>>): Boolean {
        val workbook = XSSFWorkbook()
        val sheet: Sheet = workbook.createSheet("Sheet1")

        // Create header row
        val headerRow: Row = sheet.createRow(0)
        if (data.isNotEmpty()) {
            val firstRow = data[0]
            firstRow.keys.forEachIndexed { index, key ->
                val cell: Cell = headerRow.createCell(index)
                cell.setCellValue(key)
            }
        }

        // Populate data rows
        data.forEachIndexed { rowIndex, rowData ->
            val row: Row = sheet.createRow(rowIndex + 1)
            rowData.keys.forEachIndexed { cellIndex, key ->
                val cell: Cell = row.createCell(cellIndex)
                cell.setCellValue(rowData[key])
            }
        }

        // Write to file in Downloads folder
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)

        try {
            FileOutputStream(file).use { fos ->
                workbook.write(fos)
                showNotification(context, fileName)
                Log.d("ExcelUtil", "Excel file created at: ${file.absolutePath}")
                return true
            }
        } catch (e: IOException) {
            Log.e("ExcelUtil", "Failed to create Excel file", e)
            return false
        } finally {
            try {
                workbook.close()
            } catch (e: IOException) {
                Log.e("ExcelUtil", "Failed to close workbook", e)
            }
        }
    }

    fun createExcel(context: Context, fileName: String, jsonData: JSONArray): Boolean {
        // Convert JSONArray to List<Map<String, String>>
        val data = mutableListOf<Map<String, String>>()
        try {
            for (i in 0 until jsonData.length()) {
                val jsonObject = jsonData.getJSONObject(i)
                val map = mutableMapOf<String, String>()
                jsonObject.keys().forEach { key ->
                    map[key] = jsonObject.getString(key)
                }
                data.add(map)
            }
        } catch (e: Exception) {
            Log.e("ExcelUtil", "Failed to parse JSONArray: ${e.message}")
            return false
        }
        return createExcel(context, fileName, data)
    }

    fun readExcel(context: Context, fileName: String): List<Map<String, String>> {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        val result = mutableListOf<Map<String, String>>()

        if (!file.exists()) {
            Log.e("ExcelUtil", "File $fileName does not exist")
            return result
        }

        try {
            FileInputStream(file).use { fis ->
                val workbook = XSSFWorkbook(fis)
                val sheet = workbook.getSheetAt(0)
                val headerRow = sheet.getRow(0) ?: return result
                val headers = mutableListOf<String>()
                headerRow.forEach { cell ->
                    headers.add(cell.stringCellValue)
                }

                for (rowIndex in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue
                    val rowData = mutableMapOf<String, String>()
                    headers.forEachIndexed { index, header ->
                        val cell = row.getCell(index)
                        rowData[header] = cell?.toString() ?: ""
                    }
                    result.add(rowData)
                }
                workbook.close()
            }
        } catch (e: IOException) {
            Log.e("ExcelUtil", "Failed to read Excel file", e)
        }
        return result
    }

    fun deleteExcel(context: Context, fileName: String): Boolean {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        return if (file.exists()) {
            val deleted = file.delete()
            if (deleted) {
                Log.d("ExcelUtil", "Excel file deleted: ${file.absolutePath}")
            } else {
                Log.e("ExcelUtil", "Failed to delete Excel file: ${file.absolutePath}")
            }
            deleted
        } else {
            Log.e("ExcelUtil", "File $fileName does not exist")
            false
        }
    }
}