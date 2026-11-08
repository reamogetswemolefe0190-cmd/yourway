package com.example.wififingerprintsurvey

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class WifiSurveyDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "wifi_survey.db"
        const val DATABASE_VERSION = 2

        // Table Names
        const val TABLE_SURVEYS = "survey_records"
        const val TABLE_MEASUREMENTS = "wifi_measurements"

        // Survey Columns
        const val KEY_SURVEY_ID = "id"
        const val KEY_SURVEY_LABEL = "label"
        const val KEY_SURVEY_TIMESTAMP = "timestamp"
        const val KEY_SURVEY_COORD_X = "coord_x"
        const val KEY_SURVEY_COORD_Y = "coord_y"
        const val KEY_SURVEY_FLOOR_PLAN = "floor_plan_name"

        // Measurement Columns
        const val KEY_MEASURE_ID = "id"
        const val KEY_MEASURE_RECORD_ID = "record_id"
        const val KEY_MEASURE_BSSID = "bssid"
        const val KEY_MEASURE_RSSI = "rssi"
        const val KEY_MEASURE_SSID = "ssid"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createSurveysTable = """
            CREATE TABLE $TABLE_SURVEYS (
                $KEY_SURVEY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $KEY_SURVEY_LABEL TEXT NOT NULL,
                $KEY_SURVEY_TIMESTAMP INTEGER NOT NULL,
                $KEY_SURVEY_COORD_X REAL,
                $KEY_SURVEY_COORD_Y REAL,
                $KEY_SURVEY_FLOOR_PLAN TEXT
            )
        """.trimIndent()

        val createMeasurementsTable = """
            CREATE TABLE $TABLE_MEASUREMENTS (
                $KEY_MEASURE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $KEY_MEASURE_RECORD_ID INTEGER NOT NULL,
                $KEY_MEASURE_BSSID TEXT NOT NULL,
                $KEY_MEASURE_RSSI INTEGER NOT NULL,
                $KEY_MEASURE_SSID TEXT NOT NULL,
                FOREIGN KEY($KEY_MEASURE_RECORD_ID) REFERENCES $TABLE_SURVEYS($KEY_SURVEY_ID) ON DELETE CASCADE
            )
        """.trimIndent()

        db.execSQL(createSurveysTable)
        db.execSQL(createMeasurementsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_SURVEYS ADD COLUMN $KEY_SURVEY_COORD_X REAL")
            db.execSQL("ALTER TABLE $TABLE_SURVEYS ADD COLUMN $KEY_SURVEY_COORD_Y REAL")
            db.execSQL("ALTER TABLE $TABLE_SURVEYS ADD COLUMN $KEY_SURVEY_FLOOR_PLAN TEXT")
        }
    }

    fun saveSurvey(
        label: String,
        measurements: List<WifiMeasurement>,
        coordX: Float? = null,
        coordY: Float? = null,
        floorPlanName: String? = null
    ): Long {
        val db = writableDatabase
        db.beginTransaction()
        var recordId: Long = -1
        try {
            val surveyValues = ContentValues().apply {
                put(KEY_SURVEY_LABEL, label)
                put(KEY_SURVEY_TIMESTAMP, System.currentTimeMillis())
                if (coordX != null) put(KEY_SURVEY_COORD_X, coordX)
                if (coordY != null) put(KEY_SURVEY_COORD_Y, coordY)
                if (floorPlanName != null) put(KEY_SURVEY_FLOOR_PLAN, floorPlanName)
            }
            recordId = db.insert(TABLE_SURVEYS, null, surveyValues)

            if (recordId != -1L) {
                for (m in measurements) {
                    val measureValues = ContentValues().apply {
                        put(KEY_MEASURE_RECORD_ID, recordId)
                        put(KEY_MEASURE_BSSID, m.bssid)
                        put(KEY_MEASURE_RSSI, m.rssi)
                        put(KEY_MEASURE_SSID, m.ssid)
                    }
                    db.insert(TABLE_MEASUREMENTS, null, measureValues)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return recordId
    }

    fun deleteSurvey(recordId: Long): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_SURVEYS, "$KEY_SURVEY_ID = ?", arrayOf(recordId.toString())) > 0
    }

    fun getAllSurveys(): List<SurveyRecord> {
        val surveysList = mutableListOf<SurveyRecord>()
        val db = readableDatabase

        val selectQuery = "SELECT * FROM $TABLE_SURVEYS ORDER BY $KEY_SURVEY_TIMESTAMP DESC"
        val cursor = db.rawQuery(selectQuery, null)

        cursor.use { c ->
            val idIndex = c.getColumnIndex(KEY_SURVEY_ID)
            val labelIndex = c.getColumnIndex(KEY_SURVEY_LABEL)
            val timestampIndex = c.getColumnIndex(KEY_SURVEY_TIMESTAMP)
            val xIndex = c.getColumnIndex(KEY_SURVEY_COORD_X)
            val yIndex = c.getColumnIndex(KEY_SURVEY_COORD_Y)
            val floorPlanIndex = c.getColumnIndex(KEY_SURVEY_FLOOR_PLAN)

            if (idIndex != -1 && labelIndex != -1 && timestampIndex != -1) {
                while (c.moveToNext()) {
                    val recordId = c.getLong(idIndex)
                    val label = c.getString(labelIndex)
                    val timestamp = c.getLong(timestampIndex)
                    val coordX = if (xIndex != -1 && !c.isNull(xIndex)) c.getFloat(xIndex) else null
                    val coordY = if (yIndex != -1 && !c.isNull(yIndex)) c.getFloat(yIndex) else null
                    val floorPlanName = if (floorPlanIndex != -1 && !c.isNull(floorPlanIndex)) c.getString(floorPlanIndex) else null

                    val measurements = getMeasurementsForRecord(recordId)
                    surveysList.add(
                        SurveyRecord(
                            id = recordId,
                            label = label,
                            timestamp = timestamp,
                            measurements = measurements,
                            coordX = coordX,
                            coordY = coordY,
                            floorPlanName = floorPlanName
                        )
                    )
                }
            }
        }
        return surveysList
    }

    private fun getMeasurementsForRecord(recordId: Long): List<WifiMeasurement> {
        val measurementsList = mutableListOf<WifiMeasurement>()
        val db = readableDatabase

        val selectQuery = "SELECT * FROM $TABLE_MEASUREMENTS WHERE $KEY_MEASURE_RECORD_ID = ?"
        val cursor = db.rawQuery(selectQuery, arrayOf(recordId.toString()))

        cursor.use { c ->
            val bssidIndex = c.getColumnIndex(KEY_MEASURE_BSSID)
            val rssiIndex = c.getColumnIndex(KEY_MEASURE_RSSI)
            val ssidIndex = c.getColumnIndex(KEY_MEASURE_SSID)

            if (bssidIndex != -1 && rssiIndex != -1 && ssidIndex != -1) {
                while (c.moveToNext()) {
                    val bssid = c.getString(bssidIndex)
                    val rssi = c.getInt(rssiIndex)
                    val ssid = c.getString(ssidIndex)
                    measurementsList.add(WifiMeasurement(bssid, rssi, ssid))
                }
            }
        }
        return measurementsList
    }
}
