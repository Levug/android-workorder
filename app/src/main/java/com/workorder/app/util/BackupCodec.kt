package com.workorder.app.util

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.workorder.app.data.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

enum class TransferKind { FULL_DATABASE, CONFIGURATION }

data class BackupSnapshot(
    val operations: List<Operation>,
    val settings: Settings?,
    val workDays: List<WorkDay> = emptyList(),
    val entries: List<OperationEntry> = emptyList(),
    val monthlyPlans: List<MonthlyPlan> = emptyList(),
    val bankAllocations: List<BankAllocation> = emptyList()
)

/** A versioned logical backup: no live SQLite/WAL files are copied or replaced. */
object BackupCodec {
    private const val FULL_FORMAT = "workorder.database"
    private const val CONFIG_FORMAT = "workorder.settings"
    private val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()

    fun encode(data: BackupSnapshot, kind: TransferKind): String {
        validate(data, kind)
        val root = JsonObject().apply {
            addProperty("format", if (kind == TransferKind.FULL_DATABASE) FULL_FORMAT else CONFIG_FORMAT)
            addProperty("formatVersion", 1)
            addProperty("databaseVersion", 9)
            addProperty("createdAt", Instant.now().toString())
            add("operations", gson.toJsonTree(data.operations))
            add("settings", gson.toJsonTree(data.settings))
            if (kind == TransferKind.FULL_DATABASE) {
                add("workDays", gson.toJsonTree(data.workDays))
                add("entries", gson.toJsonTree(data.entries))
                add("monthlyPlans", gson.toJsonTree(data.monthlyPlans))
                add("bankAllocations", gson.toJsonTree(data.bankAllocations))
            }
        }
        return gson.toJson(root)
    }

    fun decode(json: String, kind: TransferKind): BackupSnapshot {
        val root = JsonParser.parseString(json.removePrefix("\uFEFF")).asObject()
        val format = root.get("format")?.string()
        val full = kind == TransferKind.FULL_DATABASE
        require(if (full) format == FULL_FORMAT else format == null || format == CONFIG_FORMAT) {
            if (full) "Это не полная копия базы. Для старого конфига выберите «Операции и настройки»."
            else "Для полной базы выберите «Импорт всей базы»."
        }
        if (format != null) {
            require(root.int("formatVersion") == 1) { "Версия формата не поддерживается. Обновите приложение." }
            require(root.int("databaseVersion") in 1..9) { "Копия из более новой версии приложения." }
        }
        val operations = root.objects("operations").map { op ->
            Operation(
                id = op.long("id", if (full) null else 0L),
                name = op.text("name"),
                durationHours = op.number("durationHours"),
                grade = op.int("grade", if (full) null else 3),
                sortOrder = op.int("sortOrder", if (full) null else 0)
            )
        }
        if (full) require(root.has("settings")) { "Отсутствует settings" }
        val settings = root.get("settings")?.takeUnless { it.isJsonNull }?.asObject()?.let { s ->
            Settings(
                id = s.int("id", if (full) null else 1),
                hourlyRate = s.number("hourlyRate", if (full) null else 0.0),
                themeMode = enumValueOf(s.text("themeMode", if (full) null else "SYSTEM")),
                themePreset = s.text("themePreset", if (full) null else "DEFAULT"),
                dynamicColor = s.bool("dynamicColor", if (full) null else false),
                defaultDayHours = s.number("defaultDayHours", if (full) null else 8.0),
                contractHourlyRate = s.number("contractHourlyRate", if (full) null else 0.0),
                workSchedule = enumValueOf(s.text("workSchedule", if (full) null else "FIVE_TWO")),
                shiftAnchorDate = s.text("shiftAnchorDate", if (full) null else "2026-01-01")
            )
        }
        val data = BackupSnapshot(
            operations = operations,
            settings = settings,
            workDays = if (!full) emptyList() else root.objects("workDays").map { d ->
                WorkDay(d.long("id"), d.text("date"), d.number("totalHours"), d.text("comment"), enumValueOf(d.text("dayType")))
            },
            entries = if (!full) emptyList() else root.objects("entries").map { e ->
                require(e.has("sourceEventId")) { "Отсутствует sourceEventId" }
                OperationEntry(e.long("id"), e.long("workDayId"), e.long("operationId"), e.int("quantity"),
                    e.long("createdAt"), e.get("sourceEventId").takeUnless { it.isJsonNull }?.string())
            },
            monthlyPlans = if (!full) emptyList() else root.objects("monthlyPlans").map { p ->
                MonthlyPlan(p.text("yearMonth"), p.number("plannedHours"), p.number("coefficient"),
                    enumValueOf(p.text("strategy")), p.number("grade3Percent"), p.number("grade4Percent"),
                    p.number("grade5Percent"), p.number("grade6Percent"))
            },
            bankAllocations = if (!full) emptyList() else root.objects("bankAllocations").map { a ->
                BankAllocation(a.text("yearMonth"), a.long("operationId"), a.int("bankedQuantity"))
            }
        )
        validate(data, kind)
        return data
    }

    fun validate(data: BackupSnapshot, kind: TransferKind) {
        val full = kind == TransferKind.FULL_DATABASE
        data.operations.forEach {
            require(it.id >= if (full) 1 else 0) { "Некорректный ID операции" }
            require(it.name.isNotBlank()) { "Пустое название операции" }
            nonNegative(it.durationHours)
            require(it.grade in 3..6) { "Разряд должен быть от 3 до 6" }
        }
        unique(data.operations.filter { it.id > 0 }.map { it.id })
        data.settings?.let {
            require(it.id == 1) { "Некорректный ID настроек" }
            nonNegative(it.hourlyRate); nonNegative(it.contractHourlyRate); nonNegative(it.defaultDayHours)
            require(it.defaultDayHours > 0) { "Продолжительность смены должна быть положительной" }
            date(it.shiftAnchorDate)
        }
        if (!full) return
        unique(data.workDays.map { it.id }); unique(data.workDays.map { it.date })
        unique(data.entries.map { it.id }); unique(data.entries.mapNotNull { it.sourceEventId })
        unique(data.monthlyPlans.map { it.yearMonth })
        unique(data.bankAllocations.map { it.yearMonth to it.operationId })
        val operationIds = data.operations.map { it.id }.toSet()
        val dayIds = data.workDays.map { it.id }.toSet()
        data.workDays.forEach { require(it.id > 0); date(it.date); nonNegative(it.totalHours) }
        data.entries.forEach {
            require(it.id > 0 && it.createdAt >= 0) { "Некорректная запись журнала" }
            require(it.workDayId in dayIds && it.operationId in operationIds) { "Журнал ссылается на отсутствующий день или операцию" }
            require(it.sourceEventId == null || it.sourceEventId.isNotBlank()) { "Пустой идентификатор события часов" }
        }
        data.monthlyPlans.forEach {
            month(it.yearMonth); nonNegative(it.plannedHours); nonNegative(it.coefficient)
            listOf(it.grade3Percent, it.grade4Percent, it.grade5Percent, it.grade6Percent).forEach(::nonNegative)
        }
        data.bankAllocations.forEach {
            month(it.yearMonth)
            require(it.operationId in operationIds) { "Банк ссылается на отсутствующую операцию" }
        }
    }

    private fun date(value: String) { require(LocalDate.parse(value).toString() == value) { "Некорректная дата" } }
    private fun month(value: String) { require(YearMonth.parse(value).toString() == value) { "Некорректный месяц" } }
    private fun nonNegative(value: Double) { require(value.isFinite() && value >= 0) { "Некорректное числовое значение" } }
    private fun <T> unique(values: List<T>) { require(values.size == values.toSet().size) { "В файле есть повторяющиеся идентификаторы" } }
    private fun JsonElement.asObject(): JsonObject { require(isJsonObject) { "Ожидался JSON-объект" }; return asJsonObject }
    private fun JsonElement.string(): String { require(isJsonPrimitive && asJsonPrimitive.isString) { "Ожидался текст" }; return asString }
    private fun JsonObject.objects(name: String): List<JsonObject> {
        val value = get(name)
        require(value != null && value.isJsonArray) { "Отсутствует список $name" }
        return value.asJsonArray.map { it.asObject() }
    }
    private fun JsonObject.text(name: String, default: String? = null): String =
        get(name)?.string() ?: requireNotNull(default) { "Отсутствует поле $name" }
    private fun JsonObject.long(name: String, default: Long? = null): Long {
        val value = get(name) ?: return requireNotNull(default) { "Отсутствует поле $name" }
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "Ожидалось число $name" }
        return value.asBigDecimal.longValueExact()
    }
    private fun JsonObject.int(name: String, default: Int? = null): Int = Math.toIntExact(long(name, default?.toLong()))
    private fun JsonObject.number(name: String, default: Double? = null): Double {
        val value = get(name) ?: return requireNotNull(default) { "Отсутствует поле $name" }
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "Ожидалось число $name" }
        return value.asDouble.also { require(it.isFinite()) { "Некорректное число $name" } }
    }
    private fun JsonObject.bool(name: String, default: Boolean? = null): Boolean {
        val value = get(name) ?: return requireNotNull(default) { "Отсутствует поле $name" }
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "Ожидалось логическое значение $name" }
        return value.asBoolean
    }
}
