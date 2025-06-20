package top.cywin.onetv.tv.supabase

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.cywin.onetv.core.data.repositories.supabase.SupabaseApiClient
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import kotlinx.serialization.Serializable

/**
 * 观看历史界面
 */
@Composable
fun SupabaseWatchHistory(
    userData: SupabaseUserDataIptv?,
    isLoading: Boolean,
    context: Context
) {
    val scope = rememberCoroutineScope()
    val watchHistoryItems = remember { mutableStateListOf<WatchHistoryItem>() }
    val watchStatistics = remember { mutableStateOf<WatchStatistics?>(null) }
    
    var isHistoryLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    
    // 分页参数
    var currentPage by remember { mutableStateOf(1) }
    var totalPages by remember { mutableStateOf(1) }
    var hasMoreData by remember { mutableStateOf(true) }
    
    // 筛选和排序选项
    var timeRangeFilter by remember { mutableStateOf("全部") }
    var sortOption by remember { mutableStateOf("时间") }
    var isTimeRangeMenuExpanded by remember { mutableStateOf(false) }
    var isSortOptionMenuExpanded by remember { mutableStateOf(false) }
    
    // 时间范围选项
    val timeRangeOptions = listOf("全部", "今天", "本周", "本月", "今年")
    val sortOptions = listOf("时间", "频道名", "观看时长")
    
    // 初始化会话管理器
    LaunchedEffect(Unit) {
        SupabaseWatchHistorySessionManager.initialize(context)
    }
    
    // 加载第一页观看历史
    LaunchedEffect(userData, timeRangeFilter, sortOption) {
        if (userData != null && !isLoading) {
            isHistoryLoading = true
            errorMessage = null
            currentPage = 1 // 重置为第一页
            
            try {
                Log.d("SupabaseWatchHistory", "开始加载观看历史: 时间范围=$timeRangeFilter, 排序=$sortOption")
                // 从本地会话管理器获取观看历史数据
                val (history, stats, pagination) = SupabaseWatchHistorySessionManager.getWatchHistory(
                    timeRange = timeRangeFilter,
                    sortBy = sortOption,
                    page = currentPage
                )
                
                Log.d("SupabaseWatchHistory", "加载结果: ${history.size}条记录, 统计: 总时长=${stats.totalWatchTime}, 频道数=${stats.totalChannels}")
                
                watchHistoryItems.clear()
                watchHistoryItems.addAll(history)
                watchStatistics.value = stats
                
                // 更新分页信息
                totalPages = pagination.totalPages
                hasMoreData = currentPage < totalPages
                
                // 如果本地数据为空，尝试从服务器同步
                if (history.isEmpty()) {
                    Log.d("SupabaseWatchHistory", "本地无数据，尝试从服务器同步")
                    val syncSuccess = SupabaseWatchHistorySessionManager.syncFromServer(context)
                    if (syncSuccess) {
                        Log.d("SupabaseWatchHistory", "服务器同步成功，重新加载数据")
                        // 重新加载数据
                        val (syncedHistory, syncedStats, syncedPagination) = SupabaseWatchHistorySessionManager.getWatchHistory(
                            timeRange = timeRangeFilter,
                            sortBy = sortOption,
                            page = currentPage
                        )
                        
                        watchHistoryItems.clear()
                        watchHistoryItems.addAll(syncedHistory)
                        watchStatistics.value = syncedStats
                        
                        totalPages = syncedPagination.totalPages
                        hasMoreData = currentPage < syncedPagination.totalPages
                    }
                }
            } catch (e: Exception) {
                Log.e("SupabaseWatchHistory", "加载观看历史失败", e)
                errorMessage = "加载观看历史失败: ${e.message}"
            } finally {
                isHistoryLoading = false
            }
        } else {
            isHistoryLoading = false
        }
    }
    
    // 加载更多数据函数
    fun loadMoreData() {
        if (isLoadingMore || !hasMoreData) return
        
        scope.launch {
            try {
                isLoadingMore = true
                val nextPage = currentPage + 1
                
                val (moreHistory, _, pagination) = SupabaseWatchHistorySessionManager.getWatchHistory(
                    timeRange = timeRangeFilter,
                    sortBy = sortOption,
                    page = nextPage
                )
                
                watchHistoryItems.addAll(moreHistory)
                currentPage = nextPage
                hasMoreData = currentPage < pagination.totalPages
            } catch (e: Exception) {
                Log.e("SupabaseWatchHistory", "加载更多观看历史失败", e)
                // 不显示错误，只在日志中记录
            } finally {
                isLoadingMore = false
            }
        }
    }
    
    // 检测列表滚动到底部时加载更多
    val listState = rememberLazyListState()
    
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && hasMoreData && !isLoadingMore) {
                    val totalItemsCount = watchHistoryItems.size
                    // 当滚动到最后5个项目时，加载更多
                    if (lastVisibleIndex >= totalItemsCount - 5) {
                        loadMoreData()
                    }
                }
            }
    }
    
    // 当组件被销毁时，同步数据到服务器
    DisposableEffect(Unit) {
        onDispose {
            scope.launch {
                SupabaseWatchHistorySessionManager.syncToServer(context)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isLoading || isHistoryLoading) {
            // 加载中
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFFFD700),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "加载观看历史中...",
                        color = Color.White
                    )
                }
            }
        } else if (errorMessage != null) {
            // 错误信息
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFF44336),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            // 重试加载
                            errorMessage = null
                            isHistoryLoading = true
                            scope.launch {
                                try {
                                    val (history, stats, pagination) = SupabaseWatchHistorySessionManager.getWatchHistory(
                                        timeRange = timeRangeFilter,
                                        sortBy = sortOption,
                                        page = 1
                                    )
                                    
                                    watchHistoryItems.clear()
                                    watchHistoryItems.addAll(history)
                                    watchStatistics.value = stats
                                    
                                    // 更新分页信息
                                    currentPage = 1
                                    totalPages = pagination.totalPages
                                    hasMoreData = currentPage < pagination.totalPages
                                } catch (e: Exception) {
                                    Log.e("SupabaseWatchHistory", "重试加载观看历史失败", e)
                                    errorMessage = "重试加载失败: ${e.message}"
                                } finally {
                                    isHistoryLoading = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2C3E50)
                        )
                    ) {
                        Text("重试")
                    }
                }
            }
        } else {
            // 使用可滚动的行布局，左侧筛选项，右侧统计和列表
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 左侧筛选面板
                Column(
                    modifier = Modifier
                        .weight(0.3f)
                        .verticalScroll(rememberScrollState())
                        .padding(end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 筛选标签组
                    Column(
                        modifier = Modifier.padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        timeRangeOptions.forEach { option ->
                            FilterChip(
                                selected = timeRangeFilter == option,
                                onClick = { 
                                    timeRangeFilter = option
                                    // 重新加载数据
                                    isHistoryLoading = true
                                    currentPage = 1
                                    scope.launch {
                                        try {
                                            val (history, stats, pagination) = SupabaseWatchHistorySessionManager.getWatchHistory(
                                                timeRange = timeRangeFilter,
                                                sortBy = sortOption,
                                                page = currentPage
                                            )
                                            
                                            watchHistoryItems.clear()
                                            watchHistoryItems.addAll(history)
                                            watchStatistics.value = stats
                                            
                                            // 更新分页信息
                                            totalPages = pagination.totalPages
                                            hasMoreData = currentPage < totalPages
                                        } catch (e: Exception) {
                                            Log.e("SupabaseWatchHistory", "加载观看历史失败", e)
                                            errorMessage = "加载观看历史失败: ${e.message}"
                                        } finally {
                                            isHistoryLoading = false
                                        }
                                    }
                                },
                                label = option,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 排序选项标签
                    Column(
                        modifier = Modifier.padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        sortOptions.forEach { option ->
                            FilterChip(
                                selected = sortOption == option,
                                onClick = { 
                                    sortOption = option
                                    // 重新加载数据
                                    isHistoryLoading = true
                                    currentPage = 1
                                    scope.launch {
                                        try {
                                            val (history, stats, pagination) = SupabaseWatchHistorySessionManager.getWatchHistory(
                                                timeRange = timeRangeFilter,
                                                sortBy = sortOption,
                                                page = currentPage
                                            )
                                            
                                            watchHistoryItems.clear()
                                            watchHistoryItems.addAll(history)
                                            watchStatistics.value = stats
                                            
                                            // 更新分页信息
                                            totalPages = pagination.totalPages
                                            hasMoreData = currentPage < totalPages
                                        } catch (e: Exception) {
                                            Log.e("SupabaseWatchHistory", "加载观看历史失败", e)
                                            errorMessage = "加载观看历史失败: ${e.message}"
                                        } finally {
                                            isHistoryLoading = false
                                        }
                                    }
                                },
                                label = option,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    
                    // 同步按钮
                    SyncWatchHistoryButton(
                        context = context,
                        isSyncing = isSyncing,
                        onSyncStart = { isSyncing = true },
                        onSyncComplete = { isSyncing = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // 右侧内容区域 (观看统计和历史记录列表)
                Column(
                    modifier = Modifier
                        .weight(0.7f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 观看统计
                    watchStatistics.value?.let { stats ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color(0xFF2C3E50).copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFFFFD700).copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 移除多余的"观看统计"标题
                                
                                // 表格式统计数据
                                WatchHistoryStatsTable(stats)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 观看历史列表
                    if (watchHistoryItems.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "暂无观看记录",
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            Text(
                                text = "观看频道后将自动记录观看历史",
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(watchHistoryItems) { item ->
                                WatchHistoryItemView(item)
                            }
                            
                            // 底部加载更多指示器
                            if (isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFFFFD700),
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 观看历史项视图
 */
@Composable
fun WatchHistoryItemView(item: WatchHistoryItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                color = Color(0xFF2C3E50).copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.channelName,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = formatDuration(item.duration),
                color = Color(0xFFFFD700)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "开始时间: ${item.watchStart}",
            fontSize = 14.sp,
            color = Color.LightGray
        )
        
        if (item.watchEnd != null) {
            Text(
                text = "结束时间: ${item.watchEnd}",
                fontSize = 14.sp,
                color = Color.LightGray
            )
        }
    }
}

/**
 * 统计项视图
 */
@Composable
fun StatItem(title: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD700)
        )
    }
}

/**
 * 格式化时长
 */
fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    
    return when {
        hours > 0 -> String.format("%d时%02d分%02d秒", hours, minutes, remainingSeconds)
        minutes > 0 -> String.format("%d分%02d秒", minutes, remainingSeconds)
        else -> String.format("%d秒", remainingSeconds)
    }
}

/**
 * 分页加载观看历史
 */
suspend fun loadWatchHistoryPaged(
    context: Context,
    timeRange: String,
    sortBy: String,
    page: Int = 1,
    pageSize: Int = 20
): Triple<List<WatchHistoryItem>, WatchStatistics, PaginationData> = withContext(Dispatchers.IO) {
    // 转换时间范围参数
    val timeRangeParam = when (timeRange) {
        "今天" -> "today"
        "本周" -> "week"
        "本月" -> "month"
        "今年" -> "year"
        else -> "all"
    }
    
    // 转换排序参数
    val sortByParam = when (sortBy) {
        "频道名" -> "channel_name"
        "观看时长" -> "duration"
        else -> "watch_start"
    }
    
    val sortOrderParam = when (sortBy) {
        "频道名" -> "asc"
        else -> "desc"
    }
    
    Log.d("SupabaseWatchHistory", "开始从API加载观看历史: 时间范围=$timeRangeParam, 排序=$sortByParam:$sortOrderParam, 页码=$page")
    
    val apiClient = SupabaseApiClient()
    
    try {
        // 1. 获取统计数据
        val statsResponse = apiClient.getWatchStatistics(timeRangeParam)
        Log.d("SupabaseWatchHistory", "API统计数据响应: $statsResponse")
        
        // 处理统计数据
        val statistics = try {
            val statsObject = statsResponse.jsonObject["statistics"]?.jsonObject
            
            // 解析频道统计数据
            val channelStatsArray = statsObject?.get("channelStatistics")?.jsonArray
            val channelStats = mutableListOf<ChannelStatistic>()
            
            if (channelStatsArray != null) {
                for (i in 0 until channelStatsArray.size) {
                    try {
                        val statObj = channelStatsArray[i].jsonObject
                        val channelName = statObj["channelName"]?.jsonPrimitive?.content ?: continue
                        val watchCount = statObj["watchCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val totalDuration = statObj["totalDuration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0
                        
                        channelStats.add(
                            ChannelStatistic(
                                channelName = channelName,
                                watchCount = watchCount,
                                totalDuration = totalDuration
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("SupabaseWatchHistory", "解析频道统计项失败", e)
                    }
                }
            }
            
            Log.d("SupabaseWatchHistory", "解析到 ${channelStats.size} 条频道统计数据")
            
            WatchStatistics(
                totalWatchTime = statsObject?.get("totalWatchTime")?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                totalChannels = statsObject?.get("totalChannels")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                totalWatches = statsObject?.get("totalWatches")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                mostWatchedChannel = statsObject?.get("mostWatchedChannel")?.jsonPrimitive?.content,
                channelStatistics = channelStats
            )
        } catch (e: Exception) {
            Log.e("SupabaseWatchHistory", "解析统计数据失败", e)
            // 返回默认统计数据
            WatchStatistics(
                totalWatchTime = 0,
                totalChannels = 0,
                totalWatches = 0,
                mostWatchedChannel = null,
                channelStatistics = emptyList()
            )
        }
        
        Log.d("SupabaseWatchHistory", "解析后的统计数据: 总时长=${statistics.totalWatchTime}, 频道数=${statistics.totalChannels}, 观看次数=${statistics.totalWatches}")
        
        // 2. 获取观看历史列表
        try {
            val historyResponse = apiClient.getWatchHistory(
                page = page,
                pageSize = pageSize,
                timeRange = timeRangeParam,
                sortBy = sortByParam,
                sortOrder = sortOrderParam
            )
            
            Log.d("SupabaseWatchHistory", "API历史记录响应: $historyResponse")
            
            val historyItems = try {
                val itemsArray = historyResponse.jsonObject["items"]?.jsonArray
                val items = mutableListOf<WatchHistoryItem>()
                
                Log.d("SupabaseWatchHistory", "API返回记录数: ${itemsArray?.size ?: 0}")
                
                itemsArray?.forEach { jsonElement ->
                    try {
                        val item = jsonElement.jsonObject
                        
                        val historyItem = WatchHistoryItem(
                            id = item["id"]?.jsonPrimitive?.content ?: "",
                            channelName = item["channel_name"]?.jsonPrimitive?.content ?: "",
                            channelUrl = item["channel_url"]?.jsonPrimitive?.content ?: "",
                            watchStart = formatDateTime(item["watch_start"]?.jsonPrimitive?.content ?: ""),
                            watchEnd = item["watch_end"]?.jsonPrimitive?.content?.let { formatDateTime(it) },
                            duration = item["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0
                        )
                        
                        items.add(historyItem)
                    } catch (e: Exception) {
                        Log.e("SupabaseWatchHistory", "解析历史项目失败", e)
                        // 不要使用continue，因为它只能在循环中使用，而这里已经在forEach循环内部
                        // 这里不需要continue，因为异常处理后会自动继续下一个项目
                    }
                }
                
                Log.d("SupabaseWatchHistory", "成功解析API记录: ${items.size}条")
                items
            } catch (e: Exception) {
                Log.e("SupabaseWatchHistory", "解析历史数据失败", e)
                // 返回空列表
                emptyList<WatchHistoryItem>()
            }
            
            val pagination = try {
                val paginationObject = historyResponse.jsonObject["pagination"]?.jsonObject
                
                PaginationData(
                    page = paginationObject?.get("page")?.jsonPrimitive?.content?.toIntOrNull() ?: page,
                    pageSize = paginationObject?.get("pageSize")?.jsonPrimitive?.content?.toIntOrNull() ?: pageSize,
                    totalItems = paginationObject?.get("totalItems")?.jsonPrimitive?.content?.toIntOrNull() ?: historyItems.size,
                    totalPages = paginationObject?.get("totalPages")?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                )
            } catch (e: Exception) {
                Log.e("SupabaseWatchHistory", "解析分页数据失败", e)
                // 返回默认分页数据
                PaginationData(
                    page = page,
                    pageSize = pageSize,
                    totalItems = historyItems.size,
                    totalPages = if (historyItems.isEmpty()) 0 else 1
                )
            }
            
            Log.d("SupabaseWatchHistory", "最终返回: ${historyItems.size}条记录, 总页数=${pagination.totalPages}")
            return@withContext Triple(historyItems, statistics, pagination)
        } catch (e: Exception) {
            Log.e("SupabaseWatchHistory", "获取历史列表失败", e)
            // 返回空数据但不抛出异常
            return@withContext Triple(
                emptyList(),
                statistics,
                PaginationData(page = page, pageSize = pageSize, totalItems = 0, totalPages = 0)
            )
        }
    } catch (e: Exception) {
        Log.e("SupabaseWatchHistory", "加载观看历史数据失败", e)
        // 返回完全空的数据
        return@withContext Triple(
            emptyList(),
            WatchStatistics(totalWatchTime = 0, totalChannels = 0, totalWatches = 0, mostWatchedChannel = null, channelStatistics = emptyList()),
            PaginationData(page = page, pageSize = pageSize, totalItems = 0, totalPages = 0)
        )
    }
}

/**
 * 分页数据类
 */
@Serializable
data class PaginationData(
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int
)

/**
 * 格式化日期时间
 */
fun formatDateTime(isoDateTime: String): String {
    try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        val date = inputFormat.parse(isoDateTime.substring(0, 19))
        return outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        return isoDateTime
    }
}

/**
 * 观看历史项数据类
 */
@Serializable
data class WatchHistoryItem(
    val id: String,
    val channelName: String,
    val channelUrl: String,
    val watchStart: String,
    val watchEnd: String?,
    val duration: Long
)

/**
 * 观看历史统计表格组件
 */
@Composable
fun WatchHistoryStatsTable(stats: WatchStatistics) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // 表头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFF2C3E50),
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                )
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "频道列表",
                color = Color(0xFFFFD700),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(2.5f)
            )
            Text(
                text = "观看频道数",
                color = Color(0xFFFFD700),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "观看时长",
                color = Color(0xFFFFD700),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.5f)
            )
            Text(
                text = "观看次数",
                color = Color(0xFFFFD700),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
        
        // 总观看数据行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2C3E50).copy(alpha = 0.5f))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "总观看时长",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(2.5f)
            )
            Text(
                text = "${stats.totalChannels}",
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatDuration(stats.totalWatchTime),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.5f)
            )
            Text(
                text = "${stats.totalWatches}",
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
        
        // 频道详细数据
        val channelStats = stats.channelStatistics ?: emptyList()
        
        if (channelStats.isNotEmpty()) {
            channelStats.forEachIndexed { index, channelStat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (index % 2 == 0) Color(0xFF2C3E50).copy(alpha = 0.3f) else Color(0xFF1A1A1A).copy(alpha = 0.3f))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${index + 1}. ${channelStat.channelName}",
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(2.5f)
                    )
                    Text(
                        text = "1", // 每个频道统计为1个频道
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatDuration(channelStat.totalDuration),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1.5f)
                    )
                    Text(
                        text = "${channelStat.watchCount}",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            // 无数据时显示提示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "暂无频道观看记录",
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 观看统计数据类
 */
@Serializable
data class WatchStatistics(
    val totalWatchTime: Long,
    val totalChannels: Int,
    val totalWatches: Int,
    val mostWatchedChannel: String?,
    val channelStatistics: List<ChannelStatistic>? = null
)

/**
 * 频道统计数据类
 */
@Serializable
data class ChannelStatistic(
    val channelName: String,
    val watchCount: Int,
    val totalDuration: Long
)

/**
 * 记录观看历史
 * 在用户观看频道后调用此函数记录观看历史
 */
suspend fun recordChannelWatch(
    context: Context,
    channelName: String,
    channelUrl: String,
    duration: Long
): Boolean = withContext(Dispatchers.IO) {
    try {
        // 确保参数有效
        if (channelName.isBlank() || channelUrl.isBlank() || duration <= 0) {
            Log.e("SupabaseWatchHistory", "记录观看历史失败: 无效参数")
            return@withContext false
        }
        
        Log.d("SupabaseWatchHistory", "记录观看历史: $channelName, 时长: $duration 秒")
        
        // 使用本地会话管理器记录观看历史
        SupabaseWatchHistorySessionManager.recordChannelWatch(
            channelName = channelName,
            channelUrl = channelUrl,
            duration = duration,
            context = context
        )
        
        return@withContext true
    } catch (e: Exception) {
        Log.e("SupabaseWatchHistory", "记录观看历史异常", e)
        return@withContext false
    }
}

/**
 * 创建测试观看历史记录
 * 用于测试观看历史功能
 */
suspend fun createTestWatchHistory(context: Context): Boolean = withContext(Dispatchers.IO) {
    try {
        Log.d("SupabaseWatchHistory", "创建测试观看历史记录")
        
        // 创建一些测试频道
        val testChannels = listOf(
            Pair("CCTV-1 综合", "http://example.com/cctv1"),
            Pair("CCTV-5 体育", "http://example.com/cctv5"),
            Pair("CCTV-6 电影", "http://example.com/cctv6"),
            Pair("湖南卫视", "http://example.com/hunan"),
            Pair("江苏卫视", "http://example.com/jiangsu")
        )
        
        // 随机观看时长(30秒到2小时)
        val random = java.util.Random()
        
        var allSuccess = true
        
        for (channel in testChannels) {
            val duration = 30L + random.nextInt(7170) // 30秒到2小时
            
            try {
                Log.d("SupabaseWatchHistory", "创建测试记录: ${channel.first}, 时长=$duration 秒")
                // 使用本地会话管理器记录测试数据
                SupabaseWatchHistorySessionManager.recordChannelWatch(
                    channelName = channel.first,
                    channelUrl = channel.second,
                    duration = duration,
                    context = context
                )
            } catch (e: Exception) {
                Log.e("SupabaseWatchHistory", "创建测试记录失败: ${channel.first}", e)
                allSuccess = false
            }
            
            // 等待一点时间，避免请求过于频繁
            delay(100)
        }
        
        // 尝试同步到服务器
        try {
            Log.d("SupabaseWatchHistory", "同步测试记录到服务器")
            SupabaseWatchHistorySessionManager.syncToServer(context)
        } catch (e: Exception) {
            Log.e("SupabaseWatchHistory", "同步测试记录到服务器失败", e)
            // 不影响返回结果
        }
        
        return@withContext allSuccess
    } catch (e: Exception) {
        Log.e("SupabaseWatchHistory", "创建测试观看历史记录失败", e)
        return@withContext false
    }
}

/**
 * 同步观看历史按钮组件
 */
@Composable
fun SyncWatchHistoryButton(
    context: Context,
    isSyncing: Boolean,
    onSyncStart: () -> Unit,
    onSyncComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var resultMessage by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                scope.launch {
                    onSyncStart()
                    resultMessage = null
                    
                    try {
                        Log.d("SupabaseWatchHistory", "开始从服务器同步观看历史")
                        // 从服务器同步数据
                        val success = SupabaseWatchHistorySessionManager.syncFromServer(context)
                        
                        resultMessage = if (success) {
                            "成功从服务器同步观看历史"
                        } else {
                            "同步观看历史失败"
                        }
                        Log.d("SupabaseWatchHistory", resultMessage ?: "同步完成")
                    } catch (e: Exception) {
                        Log.e("SupabaseWatchHistory", "同步过程中出错", e)
                        resultMessage = "同步出错: ${e.message}"
                    } finally {
                        onSyncComplete()
                    }
                    
                    // 5秒后清除消息
                    delay(5000)
                    resultMessage = null
                }
            },
            enabled = !isSyncing,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2C3E50)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("同步观看历史记录")
            }
        }
        
        // 清空本地数据按钮
        Button(
            onClick = {
                scope.launch {
                    onSyncStart()
                    resultMessage = null
                    
                    try {
                        Log.d("SupabaseWatchHistory", "开始清空本地观看历史")
                        SupabaseWatchHistorySessionManager.clearLocalHistory(context)
                        resultMessage = "已清空本地观看历史"
                        Log.d("SupabaseWatchHistory", "已清空本地观看历史")
                    } catch (e: Exception) {
                        Log.e("SupabaseWatchHistory", "清空本地数据出错", e)
                        resultMessage = "清空数据出错: ${e.message}"
                    } finally {
                        onSyncComplete()
                    }
                    
                    // 5秒后清除消息
                    delay(5000)
                    resultMessage = null
                }
            },
            enabled = !isSyncing,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2C3E50)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("清空观看历史记录")
        }
        
        resultMessage?.let {
            Text(
                text = it,
                color = if (it.contains("成功") || it.contains("已清空")) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

// 添加FilterChip组件
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFFFFD700) else Color.Gray,
                shape = RoundedCornerShape(16.dp)
            )
            .background(
                color = if (selected) Color(0xFFFFD700).copy(alpha = 0.2f) else Color(0xFF2C3E50),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFFFFD700) else Color.White,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}