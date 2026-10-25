package com.workorder.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.workorder.app.data.AppDatabase
import com.workorder.app.data.repository.BackupRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyMigrationTest {
    @Test fun versionThreeDatabaseSurvivesUpgrade() = verifyMigration(7)
    @Test fun versionThreeOneDatabaseSurvivesUpgrade() = verifyMigration(8)
    @Test fun versionThreeFourDatabaseSurvivesUpgrade() = verifyMigration(9)
    @Test fun versionThreeFiveDatabaseSurvivesUpgrade() = verifyMigration(10)
    @Test fun versionThreeSixDatabaseSurvivesUpgrade() = verifyMigration(11)
    @Test fun versionThreeSevenDatabaseSurvivesUpgrade() = verifyMigration(12)
    @Test fun versionThreeEightDatabaseSurvivesUpgrade() = verifyMigration(13)
    @Test fun versionThreeNineDatabaseSurvivesUpgrade() = verifyMigration(14)
    @Test fun versionFourZeroDatabaseSurvivesUpgrade() = verifyMigration(15)

    private fun verifyMigration(version: Int) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".qa")) { "Migration tests must run in the isolated QA app" }
        val name = "migration-$version-${System.nanoTime()}.db"
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.workorder.app.data.AppDatabase/$version.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("database") }
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            schema.getAsJsonArray("entities").forEach { element ->
                val entity = element.asJsonObject
                val table = entity.get("tableName").asString
                legacy.execSQL(entity.get("createSql").asString.replace("\${TABLE_NAME}", table))
                entity.getAsJsonArray("indices").forEach { index ->
                    legacy.execSQL(index.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", table))
                }
            }
            schema.getAsJsonArray("setupQueries").forEach { legacy.execSQL(it.asString) }
            legacy.execSQL("INSERT INTO operations (id,name,durationHours,grade${if (version >= 8) ",sortOrder" else ""}) VALUES (42,'Старая операция',0.25,5${if (version >= 8) ",7" else ""})")
            legacy.execSQL("INSERT INTO work_days (id,date,totalHours,comment,dayType) VALUES (9,'2026-07-20',11.5,'Не потерять заметку','HOLIDAY')")
            legacy.execSQL("INSERT INTO operation_entries (id,workDayId,operationId,quantity,createdAt) VALUES (77,9,42,17,1784530000123)")
            legacy.execSQL("INSERT INTO settings (id,hourlyRate,themeMode,themePreset,dynamicColor,defaultDayHours,contractHourlyRate,workSchedule,shiftAnchorDate) VALUES (1,345.5,'DARK','AMBER',0,8.5,100,'TWO_TWO','2026-07-01')")
            legacy.execSQL("INSERT INTO monthly_plans (yearMonth,plannedHours,coefficient,strategy${if (version >= 8) ",grade3Percent,grade4Percent,grade5Percent,grade6Percent" else ""}) VALUES ('2026-07',184,1.15,'PRIORITY'${if (version >= 8) ",10,20,30,40" else ""})")
            legacy.execSQL("INSERT INTO bank_allocations (yearMonth,operationId,bankedQuantity) VALUES ('2026-07',42,3)")
            if (version >= 10) legacy.execSQL("UPDATE work_days SET specialization = 'Наладка'")
            legacy.version = version
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS).build()
        try {
            val data = BackupRepository(migrated).snapshot()
            assertEquals(42L, data.operations.single().id)
            assertEquals(if (version >= 8) 7 else 42, data.operations.single().sortOrder)
            assertEquals(77L, data.entries.single().id)
            assertEquals(17, data.entries.single().quantity)
            assertEquals(1784530000123L, data.entries.single().createdAt)
            assertNull(data.entries.single().sourceEventId)
            assertEquals("Не потерять заметку", data.workDays.single().comment)
            assertEquals(11.5, data.workDays.single().totalHours, 0.0)
            assertEquals(345.5, data.settings!!.hourlyRate, 0.0)
            assertEquals(1.0, data.settings!!.interfaceScale, 0.0)
            assertFalse(data.settings!!.daySpecializationEnabled)
            assertEquals(if (version >= 10) "Наладка" else "", data.workDays.single().specialization)
            assertTrue(data.workDays.single().isRecorded)
            assertEquals(16, migrated.openHelper.readableDatabase.version)
            assertEquals(0.0, data.workDays.single().compOffHours, 0.0)
            assertTrue(data.settings!!.bankEnabled && data.settings!!.quickAddEnabled)
            assertEquals(2.0, data.workDays.single().manualMultiplier, 0.0)
            assertFalse(data.settings!!.advanceFillEnabled)
            assertEquals(0.0, data.workDays.single().manualSingleHours + data.workDays.single().manualDoubleHours, 0.0)
            assertTrue(data.settings!!.summaryQuickAddEnabled)
            assertEquals(0.0, data.settings!!.compTimeInitialHours, 0.0)
            assertEquals("AMBER", data.settings!!.themePreset)
            assertFalse(data.operations.single().archived)
            assertEquals("1,5,10,17", data.settings!!.quickAmounts)
            assertEquals(3, data.bankAllocations.single().bankedQuantity)
            assertEquals(if (version >= 8) 10.0 else 25.0, data.monthlyPlans.single().grade3Percent, 0.0)
            migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
