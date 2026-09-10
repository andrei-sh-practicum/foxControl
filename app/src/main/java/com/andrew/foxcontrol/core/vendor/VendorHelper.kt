package com.andrew.foxcontrol.core.vendor

import android.os.Build

object VendorHelper {
    enum class Vendor {
        UNKNOWN,
        XIAOMI,
        SAMSUNG,
        HUAWEI,
        HONOR,
        OPPO,
        VIVO,
        ONEPLUS
    }

    fun getVendor(): Vendor {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()

        return when {
            brand.contains("xiaomi") || manufacturer.contains("xiaomi") ||
            brand.contains("redmi") || manufacturer.contains("redmi") -> Vendor.XIAOMI

            brand.contains("samsung") || manufacturer.contains("samsung") -> Vendor.SAMSUNG
            brand.contains("huawei") || manufacturer.contains("huawei") -> Vendor.HUAWEI
            brand.contains("honor") || manufacturer.contains("honor") -> Vendor.HONOR
            brand.contains("oppo") || manufacturer.contains("oppo") -> Vendor.OPPO
            brand.contains("vivo") || manufacturer.contains("vivo") -> Vendor.VIVO
            brand.contains("oneplus") || manufacturer.contains("oneplus") -> Vendor.ONEPLUS
            else -> Vendor.UNKNOWN
        }
    }

    data class VendorInfo(
        val name: String,
        val instructions: List<Instruction>
    )

    data class Instruction(
        val title: String,
        val description: String,
        val steps: List<String>
    )

    fun getVendorInstructions(): VendorInfo {
        val vendor = getVendor()

        return when (vendor) {
            Vendor.XIAOMI -> getMiuiInstructions()
            Vendor.SAMSUNG -> getSamsungInstructions()
            Vendor.HUAWEI -> getHuaweiInstructions()
            Vendor.HONOR -> getHonorInstructions()
            Vendor.OPPO -> getOppoInstructions()
            Vendor.VIVO -> getVivoInstructions()
            Vendor.ONEPLUS -> getOnePlusInstructions()
            Vendor.UNKNOWN -> getGenericInstructions()
        }
    }

    private fun getMiuiInstructions(): VendorInfo {
        return VendorInfo(
            name = "Xiaomi / Redmi / POCO (MIUI/HyperOS)",
            instructions = listOf(
                Instruction(
                    title = "1. Отключить оптимизацию батареи",
                    description = "MIUI агрессивно убивает фоновые процессы",
                    steps = listOf(
                        "Настройки → Приложения → Приложения",
                        "Нажмите на три точки → Особый доступ → Оптимизация батареи",
                        "Измените на 'Не оптимизировать'",
                        "Найдите Fox Control и выберите 'Не оптимизировать'"
                    )
                ),
                Instruction(
                    title = "2. Зафиксировать приложение в памяти",
                    description = "Закрепите приложение в недавних",
                    steps = listOf(
                        "Откройте недавние приложения (кнопка многозадачности)",
                        "Найдите Fox Control",
                        "Свайпните вверх или нажмите иконку замка для фиксации"
                    )
                ),
                Instruction(
                    title = "3. Автозапуск",
                    description = "Разрешите автозапуск",
                    steps = listOf(
                        "Настройки → Приложения → Приложения",
                        "Найдите Fox Control",
                        "Включите 'Автозапуск'"
                    )
                ),
                Instruction(
                    title = "4. Экономия энергии",
                    description = "Добавьте в исключения энергосбережения",
                    steps = listOf(
                        "Настройки → Батарея → Экономия энергии",
                        "Добавьте Fox Control в список исключений"
                    )
                )
            )
        )
    }

    private fun getSamsungInstructions(): VendorInfo {
        return VendorInfo(
            name = "Samsung (One UI)",
            instructions = listOf(
                Instruction(
                    title = "1. Оптимизация батареи — фоновое использование",
                    description = "One UI имеет 3 уровня оптимизации",
                    steps = listOf(
                        "Настройки → Приложения → Fox Control",
                        "Батарея → Фоновое использование",
                        "Выберите 'Автоматически разрешать'"
                    )
                ),
                Instruction(
                    title = "2. Неограниченный доступ",
                    description = "Защитите от Clean Memory",
                    steps = listOf(
                        "Настройки → Приложения → Fox Control",
                        "Батарея → Неограниченный",
                        "Включите переключатель"
                    )
                ),
                Instruction(
                    title = "3. Автозапуск",
                    description = "Разрешите автозапуск",
                    steps = listOf(
                        "Настройки → Приложения → Fox Control",
                        "Включите 'Автозапуск'"
                    )
                ),
                Instruction(
                    title = "4. Защищенные приложения",
                    description = "Закрепите в RAM Manager",
                    steps = listOf(
                        "Настройки → Безопасность и конфиденциальность → RAM Manager",
                        "Или: Настройки → Батарея и питание → Управление питанием",
                        "Добавьте Fox Control в защищенные"
                    )
                )
            )
        )
    }

    private fun getHuaweiInstructions(): VendorInfo {
        return VendorInfo(
            name = "Huawei (EMUI/HarmonyOS)",
            instructions = listOf(
                Instruction(
                    title = "1. Защита приложения",
                    description = "EMUI агрессивно закрывает фоновые процессы",
                    steps = listOf(
                        "Откройте недавние приложения",
                        "Свайпните Fox Control вверх или нажмите иконку замка",
                        "Это закрепит приложение в памяти"
                    )
                ),
                Instruction(
                    title = "2. Автозапуск",
                    description = "Разрешите автозапуск",
                    steps = listOf(
                        "Настройки → Приложения → Автозапуск",
                        "Включите Fox Control"
                    )
                ),
                Instruction(
                    title = "3. Защита батареи",
                    description = "Добавьте в защищенные приложения",
                    steps = listOf(
                        "Настройки → Батарея → Дополнительная установка",
                        "Добавьте Fox Control в защищенные приложения"
                    )
                ),
                Instruction(
                    title = "4. Отключить чистку памяти",
                    description = "Запретите чистить приложение",
                    steps = listOf(
                        "Настройки → Батарея → Завершение работы приложений",
                        "Найдите Fox Control и отключите автозавершение"
                    )
                )
            )
        )
    }

    private fun getHonorInstructions(): VendorInfo {
        return VendorInfo(
            name = "Honor (MagicOS)",
            instructions = listOf(
                Instruction(
                    title = "1. Защита приложения",
                    description = "Закрепите в недавних",
                    steps = listOf(
                        "Откройте недавние приложения",
                        "Нажмите и удерживайте Fox Control",
                        "Включите 'Защита' (иконка замка)"
                    )
                ),
                Instruction(
                    title = "2. Автозапуск",
                    description = "Разрешите автозапуск",
                    steps = listOf(
                        "Настройки → Приложения → Автозапуск",
                        "Включите Fox Control"
                    )
                ),
                Instruction(
                    title = "3. Оптимизация батареи",
                    description = "Отключите оптимизацию",
                    steps = listOf(
                        "Настройки → Батарея → Дополнительная установка",
                        "Добавьте Fox Control в защищенные"
                    )
                )
            )
        )
    }

    private fun getOppoInstructions(): VendorInfo {
        return VendorInfo(
            name = "OPPO / Realme (ColorOS)",
            instructions = listOf(
                Instruction(
                    title = "1. Автозапуск",
                    description = "Разрешите автозапуск",
                    steps = listOf(
                        "Настройки → Приложения → Автозапуск",
                        "Включите Fox Control"
                    )
                ),
                Instruction(
                    title = "2. Батарея — фоновое использование",
                    description = "Разрешите фоновую работу",
                    steps = listOf(
                        "Настройки → Батарея → Дополнительные настройки",
                        "Найдите Fox Control",
                        "Включите 'Разрешить фоновую активность'"
                    )
                ),
                Instruction(
                    title = "3. Защита приложения",
                    description = "Закрепите в недавних",
                    steps = listOf(
                        "Откройте недавние приложения",
                        "Нажмите и удерживайте Fox Control",
                        "Включите 'Защита'"
                    )
                )
            )
        )
    }

    private fun getVivoInstructions(): VendorInfo {
        return VendorInfo(
            name = "VIVO (Funtouch/OriginOS)",
            instructions = listOf(
                Instruction(
                    title = "1. Автозапуск",
                    description = "Разрешите автозапуск",
                    steps = listOf(
                        "Настройки → Приложения и уведомления → Автозапуск",
                        "Включите Fox Control"
                    )
                ),
                Instruction(
                    title = "2. Батарея — фоновое использование",
                    description = "Разрешите фоновую работу",
                    steps = listOf(
                        "Настройки → Батарея",
                        "Найдите Fox Control",
                        "Включите 'Фоновое использование'"
                    )
                ),
                Instruction(
                    title = "3. Защита в RAM",
                    description = "Закрепите в памяти",
                    steps = listOf(
                        "Откройте недавние приложения",
                        "Нажмите и удерживайте Fox Control",
                        "Включите 'Защита'"
                    )
                )
            )
        )
    }

    private fun getOnePlusInstructions(): VendorInfo {
        return VendorInfo(
            name = "OnePlus (OxygenOS)",
            instructions = listOf(
                Instruction(
                    title = "1. Автозапуск",
                    description = "Разрешите автозапуск",
                    steps = listOf(
                        "Настройки → Приложения → Автозапуск",
                        "Включите Fox Control"
                    )
                ),
                Instruction(
                    title = "2. Батарея — оптимизация",
                    description = "Отключите оптимизацию",
                    steps = listOf(
                        "Настройки → Батарея → Оптимизация батареи",
                        "Добавьте Fox Control в исключения"
                    )
                ),
                Instruction(
                    title = "3. Защита в недавних",
                    description = "Закрепите в памяти",
                    steps = listOf(
                        "Откройте недавние приложения",
                        "Нажмите и удерживайте Fox Control",
                        "Включите 'Защита'"
                    )
                )
            )
        )
    }

    private fun getGenericInstructions(): VendorInfo {
        return VendorInfo(
            name = "Стандартный Android",
            instructions = listOf(
                Instruction(
                    title = "1. Оптимизация батареи",
                    description = "Отключите оптимизацию батареи",
                    steps = listOf(
                        "Настройки → Приложения → Fox Control",
                        "Батарея → Не оптимизировать"
                    )
                ),
                Instruction(
                    title = "2. Автозапуск",
                    description = "Разрешите автозапуск",
                    steps = listOf(
                        "Настройки → Приложения → Автозапуск",
                        "Включите Fox Control"
                    )
                ),
                Instruction(
                    title = "3. Защита в недавних",
                    description = "Закрепите в памяти",
                    steps = listOf(
                        "Откройте недавние приложения",
                        "Нажмите и удерживайте Fox Control",
                        "Включите 'Защита'"
                    )
                )
            )
        )
    }
}
