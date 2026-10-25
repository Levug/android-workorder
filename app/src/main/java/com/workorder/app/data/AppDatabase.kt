package com.workorder.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.workorder.app.data.dao.OperationDao
import com.workorder.app.data.dao.OperationEntryDao
import com.workorder.app.data.dao.BankDao
import com.workorder.app.data.dao.SettingsDao
import com.workorder.app.data.dao.WorkDayDao
import com.workorder.app.data.model.Operation
import com.workorder.app.data.model.OperationEntry
import com.workorder.app.data.model.BankAllocation
import com.workorder.app.data.model.MonthlyPlan
import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.WorkDay

@Database(
    entities = [
        Operation::class,
        WorkDay::class,
        OperationEntry::class,
        Settings::class,
        MonthlyPlan::class,
        BankAllocation::class
    ],
    version = 16,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun backupDao(): com.workorder.app.data.dao.BackupDao
    abstract fun operationDao(): OperationDao
    abstract fun workDayDao(): WorkDayDao
    abstract fun operationEntryDao(): OperationEntryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun bankDao(): BankDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE work_days ADD COLUMN comment TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE settings ADD COLUMN darkTheme INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE settings ADD COLUMN themePreset TEXT NOT NULL DEFAULT 'DEFAULT'")
            }
        }

        /**
         * v5: журнал партий вместо счётчиков.
         * - work_days: дата становится уникальной (дубликаты сливаются в самую раннюю запись);
         * - work_day_operations -> operation_entries: каждая строка превращается в запись журнала,
         *   время выставляется на полночь соответствующей даты (точнее данных нет);
         * - settings: darkTheme -> themeMode (SYSTEM/LIGHT/DARK), добавлен dynamicColor.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Слить дубликаты дат: перевесить операции на самый ранний id, лишние дни удалить
                database.execSQL(
                    """
                    UPDATE work_day_operations SET workDayId = (
                        SELECT MIN(wd2.id) FROM work_days wd2
                        WHERE wd2.date = (SELECT wd3.date FROM work_days wd3 WHERE wd3.id = work_day_operations.workDayId)
                    )
                    """
                )
                database.execSQL(
                    "DELETE FROM work_days WHERE id NOT IN (SELECT MIN(id) FROM work_days GROUP BY date)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_work_days_date ON work_days(date)"
                )

                // Журнал партий
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS operation_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workDayId INTEGER NOT NULL,
                        operationId INTEGER NOT NULL,
                        quantity INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(workDayId) REFERENCES work_days(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(operationId) REFERENCES operations(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_operation_entries_workDayId ON operation_entries(workDayId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_operation_entries_operationId ON operation_entries(operationId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_operation_entries_createdAt ON operation_entries(createdAt)"
                )
                database.execSQL(
                    """
                    INSERT INTO operation_entries (workDayId, operationId, quantity, createdAt)
                    SELECT wdo.workDayId, wdo.operationId, wdo.count,
                           CAST(strftime('%s', wd.date) AS INTEGER) * 1000
                    FROM work_day_operations wdo
                    INNER JOIN work_days wd ON wd.id = wdo.workDayId
                    """
                )
                database.execSQL("DROP TABLE work_day_operations")

                // Настройки: darkTheme -> themeMode, + dynamicColor
                database.execSQL(
                    """
                    CREATE TABLE settings_new (
                        id INTEGER PRIMARY KEY NOT NULL,
                        hourlyRate REAL NOT NULL,
                        themeMode TEXT NOT NULL,
                        themePreset TEXT NOT NULL,
                        dynamicColor INTEGER NOT NULL
                    )
                    """
                )
                database.execSQL(
                    """
                    INSERT INTO settings_new (id, hourlyRate, themeMode, themePreset, dynamicColor)
                    SELECT id, hourlyRate,
                           CASE WHEN darkTheme = 1 THEN 'DARK' ELSE 'LIGHT' END,
                           themePreset, 0
                    FROM settings
                    """
                )
                database.execSQL("DROP TABLE settings")
                database.execSQL("ALTER TABLE settings_new RENAME TO settings")
            }
        }

        /** v6: настраиваемая продолжительность смены по умолчанию. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE settings ADD COLUMN defaultDayHours REAL NOT NULL DEFAULT 8.0"
                )
            }
        }

        /** v7: разряды, график/ставки, тип дня и независимый расчётный слой банка. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE operations ADD COLUMN grade INTEGER NOT NULL DEFAULT 3")
                database.execSQL("ALTER TABLE work_days ADD COLUMN dayType TEXT NOT NULL DEFAULT 'AUTO'")
                database.execSQL("ALTER TABLE settings ADD COLUMN contractHourlyRate REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE settings ADD COLUMN workSchedule TEXT NOT NULL DEFAULT 'FIVE_TWO'")
                database.execSQL("ALTER TABLE settings ADD COLUMN shiftAnchorDate TEXT NOT NULL DEFAULT '2026-01-01'")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS monthly_plans (
                        yearMonth TEXT NOT NULL PRIMARY KEY,
                        plannedHours REAL NOT NULL,
                        coefficient REAL NOT NULL,
                        strategy TEXT NOT NULL
                    )
                    """
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS bank_allocations (
                        yearMonth TEXT NOT NULL,
                        operationId INTEGER NOT NULL,
                        bankedQuantity INTEGER NOT NULL,
                        PRIMARY KEY(yearMonth, operationId),
                        FOREIGN KEY(operationId) REFERENCES operations(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_bank_allocations_operationId ON bank_allocations(operationId)")
            }
        }

        /** v8: пользовательский порядок операций и доли разрядов для банка. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE operations ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE operations SET sortOrder = id")
                database.execSQL("ALTER TABLE monthly_plans ADD COLUMN grade3Percent REAL NOT NULL DEFAULT 25.0")
                database.execSQL("ALTER TABLE monthly_plans ADD COLUMN grade4Percent REAL NOT NULL DEFAULT 25.0")
                database.execSQL("ALTER TABLE monthly_plans ADD COLUMN grade5Percent REAL NOT NULL DEFAULT 25.0")
                database.execSQL("ALTER TABLE monthly_plans ADD COLUMN grade6Percent REAL NOT NULL DEFAULT 25.0")
            }
        }

        /** v9: защита синхронизации с часами от повторной доставки одного события. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE operation_entries ADD COLUMN sourceEventId TEXT")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_operation_entries_sourceEventId " +
                        "ON operation_entries(sourceEventId)"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE settings ADD COLUMN interfaceScale REAL NOT NULL DEFAULT 1.0")
                database.execSQL("ALTER TABLE settings ADD COLUMN daySpecializationEnabled INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE work_days ADD COLUMN specialization TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Preserve every existing attendance record, including explicitly recorded zero hours.
                database.execSQL("ALTER TABLE work_days ADD COLUMN isRecorded INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** v12: архив операций вместо удаления истории и настраиваемые быстрые партии. */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE operations ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE settings ADD COLUMN quickAmounts TEXT NOT NULL DEFAULT '1,5,10,17'")
            }
        }

        /** v13: отгулы и ручной режим часов, своя тема, отключаемое быстрое добавление в сводке. */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE work_days ADD COLUMN manualSingleHours REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE work_days ADD COLUMN manualDoubleHours REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE settings ADD COLUMN customThemeColor INTEGER NOT NULL DEFAULT -12492911")
                database.execSQL("ALTER TABLE settings ADD COLUMN summaryQuickAddEnabled INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE settings ADD COLUMN compTimeInitialHours REAL NOT NULL DEFAULT 0")
            }
        }

        /** v14: экспериментальный расчёт аванса с переработками вместо пропущенных дней. */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE settings ADD COLUMN advanceFillEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v15: свой коэффициент ручного дня и отключаемые банк, свайп дней, цвета календаря, быстрые кнопки. */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE work_days ADD COLUMN manualMultiplier REAL NOT NULL DEFAULT 2")
                database.execSQL("ALTER TABLE settings ADD COLUMN bankEnabled INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE settings ADD COLUMN daySwipeEnabled INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE settings ADD COLUMN calendarHeatEnabled INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE settings ADD COLUMN quickAddEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** v16: отгул на часть дня (БС — новое значение dayType, столбец не нужен). */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE work_days ADD COLUMN compOffHours REAL NOT NULL DEFAULT 0")
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16
        )

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "work_order_database"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
