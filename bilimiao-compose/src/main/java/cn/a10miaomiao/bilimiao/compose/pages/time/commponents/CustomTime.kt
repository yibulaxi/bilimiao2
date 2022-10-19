package cn.a10miaomiao.bilimiao.compose.pages.time.commponents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.a10miaomiao.bilimiao.compose.pages.time.TimeSettingPageViewMode
import com.a10miaomiao.bilimiao.comm.store.model.DateModel
import com.a10miaomiao.bilimiao.comm.utils.DebugMiao
import java.util.*
import kotlin.math.min

@Composable
fun monthStr(y: Int, m: Int): String {
    if (m > 12) {
        return "${y+1}年1月"
    } else if (m < 1) {
        return "${y-1}年12月"
    } else {
        return "${y}年${m}月"
    }
}

enum class TextBoxStatus {
    Enable,
    Start,
    Middle,
    End,
    Disable,
}

@Composable
fun TextBox(
    modifier: Modifier,
    text: String,
    textColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    status: TextBoxStatus = TextBoxStatus.Enable
) {
    Surface(
        modifier = if (onClick != null) {
            modifier.clickable(onClick = onClick)
        } else {
            modifier
        }.padding(vertical = 2.dp),
        color = if (status == TextBoxStatus.Start || status == TextBoxStatus.End) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        shape = RoundedCornerShape(5.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .let {
                      if (status == TextBoxStatus.Middle) {
                          it.background(MaterialTheme.colorScheme.secondaryContainer)
                      } else it
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                color = textColor
            )
        }
    }
}

@Composable
fun MonthTextBox(
    year: Int,
    month: Int,
    textColor: Color = Color.Unspecified,
    arrowLeft: Boolean = false,
    arrowRight: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .size(120.dp, 40.dp)
            .clickable(enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (enabled) {
            if (arrowLeft) {
                Icon(
                    Icons.Outlined.KeyboardArrowLeft,
                    "上月",
                    modifier = Modifier.size(20.dp),
                    tint = textColor,
                )
            }
            Text(
                text = monthStr(year, month),
                fontSize = 16.sp,
                color = textColor,
            )
            if (arrowRight) {
                Icon(
                    Icons.Outlined.KeyboardArrowRight,
                    "下月",
                    modifier = Modifier.size(20.dp),
                    tint = textColor,
                )
            }
        }
    }

}

@Composable
fun Header(
    year: MutableState<Int>,
    month: MutableState<Int>,
    maxDate: DateModel,
    minDate: DateModel,
) {
    val titles = remember {
        listOf("一", "二", "三", "四", "五", "六", "日")
    }
    Column() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            MonthTextBox(
                year = year.value,
                month = month.value - 1,
                textColor = MaterialTheme.colorScheme.outline,
                arrowLeft = true,
                enabled = year.value != minDate.year || month.value != minDate.month,
                onClick = {
                    if (month.value == 1) {
                        month.value = 12
                        year.value--
                    } else {
                        month.value--
                    }
                }
            )
            MonthTextBox(
                year = year.value,
                month = month.value,
                textColor = MaterialTheme.colorScheme.onBackground,
                onClick = {
                }
            )
            MonthTextBox(
                year = year.value,
                month = month.value + 1,
                textColor = MaterialTheme.colorScheme.outline,
                arrowRight = true,
                enabled = year.value != maxDate.year || month.value != maxDate.month,
                onClick = {
                    if (month.value == 12) {
                        month.value = 1
                        year.value++
                    } else {
                        month.value++
                    }
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            titles.forEachIndexed { index, s ->
                TextBox(
                    text = s,
                    modifier = Modifier.weight(1f),
                    textColor = if (index > 4) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
        }
    }
}

@Composable
fun CustomTime(
    viewModel: TimeSettingPageViewMode
) {
    val customTime = viewModel.customTime.collectAsState()
    val timeFrom = customTime.value.timeFrom
    val timeTo = customTime.value.timeTo
    val maxDate = viewModel.maxDate
    val minDate = viewModel.minDate

    val year = remember {
        mutableStateOf(2009)
    }
    val month = remember {
        mutableStateOf(9)
    }
    val monthStartWeek = remember(year.value, month.value) {
        getWeek(year.value, month.value, 1)
    }
    val monthDayNum = remember(year.value, month.value) {
        getMonthDayNum(year.value, month.value)
    }

    var startTime by remember {
        mutableStateOf<DateModel?>(null)
    }
    var endTime by remember {
        mutableStateOf<DateModel?>(null)
    }

    val itemClick = remember(viewModel) {
        { i: Int ->
            val _startTime = startTime
            if (_startTime == null) {
                startTime = DateModel().also {
                    it.year = year.value
                    it.month = month.value
                    it.date = i
                }
            } else if (endTime == null) {
                val dateModel = DateModel().also {
                    it.year = year.value
                    it.month = month.value
                    it.date = i
                }
                if (dateModel.year >= _startTime.year
                    && dateModel.month >= _startTime.month
                    && dateModel.date > _startTime.date
                ) {
                    endTime = dateModel
                } else {
                    endTime = _startTime
                    startTime = dateModel
                }
                viewModel.setCustomTime(startTime, endTime)
            } else {
                startTime = null
                endTime = null
                viewModel.setCustomTime(null, null)
            }
            Unit
        }
    }

    Column() {
        Header(
            year = year,
            month = month,
            maxDate = maxDate,
            minDate = minDate,
        )

        Column(
            modifier = Modifier.height(210.dp)
        ) {
            var total = monthDayNum + monthStartWeek
            var num = 0
            while (num < total) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    for (dayOfWeek in num..(num + 6)) {
                        val day = dayOfWeek - monthStartWeek + 1
                        val curTime = DateModel().also {
                            it.year = year.value
                            it.month = month.value
                            it.date = day
                        }
                        val _startTime = startTime
                        val _endTime = endTime
                        val status = if (_startTime == null) {
                            TextBoxStatus.Enable
                        } else if (!curTime.diff(_startTime)) {
                            TextBoxStatus.Start
                        } else if (_endTime == null) {
                            TextBoxStatus.Enable
                        } else if (!curTime.diff(_endTime)) {
                            TextBoxStatus.End
                        } else {
                            if (_startTime.getGapCount(curTime) > 0
                                && _endTime.getGapCount(curTime) < 0
                            ) {
                                TextBoxStatus.Middle
                            } else {
                                TextBoxStatus.Enable
                            }
                        }
                        if (day in 1..monthDayNum) {
                            TextBox(
                                text =  day.toString(),
                                modifier = Modifier.weight(1f),
                                textColor = if (dayOfWeek % 7 > 4) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                status = status,
                                onClick = {
                                    itemClick(day)
                                }
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                num += 7
            }
        }
        Text(
            text = "已选择时间线：${startTime?.getValue("-") ?: "未选择"} 至 ${endTime?.getValue("-") ?: "未选择"}",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

//@Preview
//@Composable
//fun CustomTimePreview() {
//    CurrentTime()
//}