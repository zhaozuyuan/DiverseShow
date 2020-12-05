package com.four.common_util.log

enum class LogLevel(value: Int) {

    NONE(0), DEBUG(1), INFO(2), ERROR(3);

    private val level: Int = value

    fun isBigger(levelEnum: LogLevel) = this.level > levelEnum.level

    fun isBiggerOrEqual(levelEnum: LogLevel) = this.level >= levelEnum.level
}