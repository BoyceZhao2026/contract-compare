<template>
  <div class="history-tab">
    <!-- 搜索区域 -->
    <div class="search-section">
      <div class="search-container">
        <div class="search-form">
          <div class="search-item">
            <label class="search-label">文件名称搜索：</label>
            <el-input
              v-model="searchForm.filename"
              placeholder="请输入文件名称"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
            />
          </div>
          <div class="search-item">
            <label class="search-label">比对日期范围：</label>
            <el-date-picker
              v-model="searchForm.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width: 240px"
              @change="handleSearch"
            />
          </div>
          <div class="search-actions">
            <el-button type="primary" @click="handleSearch">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
            <el-button @click="handleReset">
              <el-icon><RefreshRight /></el-icon>
              重置
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 历史记录列表 -->
    <div class="history-section">
      <el-card class="history-card">
        <div class="card-header">
          <h3>
            <el-icon><List /></el-icon>
            比对记录
          </h3>
          <div class="header-tools">
            <el-button @click="refreshData" :loading="loading">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>

        <div v-loading="loading" class="history-list">
          <div
            v-for="record in historyList"
            :key="record.batch_id"
            class="history-item"
            @click="showDetailDialog(record)"
          >
            <div class="item-header">
              <div class="item-time">
                <el-icon><Clock /></el-icon>
                {{ formatDate(record.create_time) }}
              </div>
            </div>
            <div class="item-content">
              <div class="file-group">
                <div class="file-label">
                  <el-icon><Folder /></el-icon>
                  原文件
                </div>
                <div class="file-list-with-download">
                  <div class="file-tags">
                    <el-tag
                      v-for="(filename, index) in parseFilenames(record.original_filenames)"
                      :key="'orig-' + index"
                      type="info"
                      size="small"
                    >
                      {{ filename }}
                    </el-tag>
                  </div>
                </div>
              </div>

              <div class="vs-divider">VS</div>

              <div class="file-group">
                <div class="file-label">
                  <el-icon><FolderOpened /></el-icon>
                  目标文件
                </div>
                <div class="file-list-with-download">
                  <div class="file-tags">
                    <el-tag
                      v-for="(filename, index) in parseFilenames(record.target_filenames)"
                      :key="'target-' + index"
                      type="success"
                      size="small"
                    >
                      {{ filename }}
                    </el-tag>
                  </div>
                </div>
              </div>
            </div>

            <div class="item-actions">
              <el-button link type="primary" @click.stop="replayComparisonFromList(record)">
                <el-icon><View /></el-icon>
                重新比对
              </el-button>
              <el-button link type="primary" @click.stop="downloadFile(record, 'original')">
                <el-icon><Download /></el-icon>
                下载原文件
              </el-button>
              <el-button link type="primary" @click.stop="downloadFile(record, 'target')">
                <el-icon><Download /></el-icon>
                下载目标文件
              </el-button>
            </div>
          </div>

          <!-- 空状态 -->
          <el-empty
            v-if="!loading && historyList.length === 0"
            description="暂无比对记录"
          />
        </div>

        <!-- 分页 -->
        <div v-if="total > 0" class="pagination-wrapper">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </el-card>
    </div>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="showDetailDialogVisible"
      :title="`比对详情 - ${formatDate(selectedRecord?.create_time || '')}`"
      width="95%"
      :close-on-click-modal="false"
      @close="handleDetailClose"
    >
      <div v-loading="detailLoading">
        <!-- 详情信息 -->
        <div v-if="detailRecords.length > 0" class="detail-info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="原文件">
              {{ detailRecords[0]?.originalFilename }}
            </el-descriptions-item>
            <el-descriptions-item label="目标文件">
              {{ detailRecords[0]?.targetFilename }}
            </el-descriptions-item>
            <el-descriptions-item label="比对时间">
              {{ formatDate(selectedRecord?.create_time || '') }}
            </el-descriptions-item>
            <el-descriptions-item label="文件数量">
              {{ detailRecords.length }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 比对结果展示 -->
        <div v-if="showDiffResult" class="diff-result">
          <div class="diff-controls">
            <el-button @click="handleDetailClose">
              <el-icon><Close /></el-icon>
              关闭
            </el-button>
          </div>
          <DiffViewer
            :key="`diff-${currentOriginalPath}-${currentTargetPath}`"
            ref="diffViewerRef"
            :left-path="currentOriginalPath"
            :right-path="currentTargetPath"
          />
        </div>

        <!-- 加载状态 -->
        <div v-else class="loading-container">
          <el-skeleton :rows="10" animated />
          <div class="loading-text">正在加载比对结果...</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Clock, Folder, FolderOpened, List, Refresh, Search, RefreshRight,
  View, Close, Download
} from '@element-plus/icons-vue'
import axios from 'axios'
import DiffViewer from './DiffViewer.vue'
import { useRouter } from 'vue-router'

const router = useRouter()

interface HistoryRecord {
  batch_id: string
  create_time: string
  original_filenames: string
  target_filenames: string
}

interface DetailRecord {
  id: number
  batchId: string
  originalFilename: string
  targetFilename: string
  originalFilePath: string
  targetFilePath: string
  createTime: string
}

// 响应式数据
const loading = ref(false)
const detailLoading = ref(false)
const historyList = ref<HistoryRecord[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const showDetailDialogVisible = ref(false)
const selectedRecord = ref<HistoryRecord | null>(null)
const detailRecords = ref<DetailRecord[]>([])
const diffViewerRef = ref<InstanceType<typeof DiffViewer> | null>(null)
const showDiffResult = ref(false)
const currentOriginalPath = ref('')
const currentTargetPath = ref('')

// 搜索表单数据
const searchForm = ref({
  filename: '',
  dateRange: [] as string[]
})

// 解析文件名字符串
const parseFilenames = (filenames: string) => {
  if (!filenames) return []
  return filenames.split(',').map(f => f.trim()).filter(f => f)
}

// 刷新数据
const refreshData = () => {
  fetchHistory()
}

// 从列表重新比对
const replayComparisonFromList = async (record: HistoryRecord) => {
  try {
    const details = await axios.get(`/contract/history/${record.batch_id}`)
    if (details.data.code === 200 && details.data.data.length > 0) {
      const firstRecord = details.data.data[0]
      // 切换到第一个tab并跳转
      router.push({
        path: '/',
        query: {
          original: firstRecord.originalFilePath,
          target: firstRecord.targetFilePath
        }
      })
    }
  } catch (error) {
    ElMessage.error('获取文件信息失败')
  }
}

// 下载文件
const downloadFile = async (record: HistoryRecord, type: 'original' | 'target') => {
  try {
    const details = await axios.get(`/contract/history/${record.batch_id}`)
    if (details.data.code !== 200 || details.data.data.length === 0) {
      ElMessage.error('获取文件信息失败')
      return
    }

    const fileRecord = details.data.data[0]
    let filePath: string
    let fileName: string

    if (type === 'original') {
      filePath = fileRecord.originalFilePath
      fileName = fileRecord.originalFilename
    } else {
      filePath = fileRecord.targetFilePath
      fileName = fileRecord.targetFilename
    }

    if (!filePath) {
      ElMessage.error(`${type === 'original' ? '原' : '目标'}文件路径不存在`)
      return
    }

    const response = await axios.get('/contract/file/stream', {
      params: { path: filePath },
      responseType: 'blob'
    })

    const blob = new Blob([response.data])
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName || 'document.docx'
    document.body.appendChild(link)
    link.click()

    window.URL.revokeObjectURL(url)
    document.body.removeChild(link)

    ElMessage.success('文件下载成功')
  } catch (error) {
    console.error('文件下载失败:', error)
    ElMessage.error('文件下载失败')
  }
}

// 搜索处理
const handleSearch = () => {
  currentPage.value = 1
  fetchHistory()
}

// 重置搜索
const handleReset = () => {
  searchForm.value = {
    filename: '',
    dateRange: []
  }
  currentPage.value = 1
  fetchHistory()
}

// 获取历史记录
const fetchHistory = async () => {
  loading.value = true
  try {
    const params: any = {
      page: currentPage.value,
      size: pageSize.value
    }

    if (searchForm.value.filename) {
      params.filename = searchForm.value.filename
    }
    if (searchForm.value.dateRange && searchForm.value.dateRange.length === 2) {
      params.startDate = searchForm.value.dateRange[0]
      params.endDate = searchForm.value.dateRange[1]
    }

    const response = await axios.get('/contract/history', { params })

    if (response.data.code === 200) {
      historyList.value = response.data.data.records
      total.value = response.data.data.total
    } else {
      ElMessage.error(response.data.message || '获取历史记录失败')
    }
  } catch (error) {
    console.error('获取历史记录失败:', error)
    ElMessage.error('获取历史记录失败')
  } finally {
    loading.value = false
  }
}

// 获取比对详情
const fetchDetail = async (batchId: string) => {
  detailLoading.value = true
  try {
    const response = await axios.get(`/contract/history/${batchId}`)

    if (response.data.code === 200) {
      detailRecords.value = response.data.data

      if (detailRecords.value.length > 0) {
        const firstRecord = detailRecords.value[0]
        const originalPath = firstRecord?.originalFilePath
        const targetPath = firstRecord?.targetFilePath

        if (firstRecord && originalPath && targetPath) {
          currentOriginalPath.value = originalPath
          currentTargetPath.value = targetPath

          await nextTick()
          showDiffResult.value = true

          let attempts = 0
          const waitForDiffViewer = () => {
            attempts++
            if (diffViewerRef.value) {
              startHistoryDiff()
            } else if (attempts < 20) {
              setTimeout(waitForDiffViewer, 100)
            } else {
              ElMessage.error('比对组件加载失败，请重试')
            }
          }

          setTimeout(waitForDiffViewer, 100)
        } else {
          ElMessage.error('文件路径信息不完整')
        }
      } else {
        ElMessage.error('没有找到比对记录')
      }
    } else {
      ElMessage.error(response.data.message || '获取详情失败')
    }
  } catch (error) {
    console.error('获取详情失败:', error)
    ElMessage.error('获取详情失败')
  } finally {
    detailLoading.value = false
  }
}

// 显示详情对话框
const showDetailDialog = async (record: HistoryRecord) => {
  selectedRecord.value = record
  showDetailDialogVisible.value = true
  await fetchDetail(record.batch_id)
}

// 格式化日期
const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleString('zh-CN')
}

// 分页处理
const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchHistory()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchHistory()
}

// 开始历史比对
const startHistoryDiff = async () => {
  if (!diffViewerRef.value) {
    ElMessage.error('比对组件未加载，请重试')
    return
  }

  if (!currentOriginalPath.value || !currentTargetPath.value) {
    ElMessage.error('文件路径为空，请重试')
    return
  }

  try {
    if (typeof diffViewerRef.value.startDiff !== 'function') {
      ElMessage.error('比对组件方法缺失，请重试')
      return
    }

    await diffViewerRef.value.startDiff()
  } catch (error) {
    console.error('历史比对失败:', error)
    ElMessage.error('比对失败: ' + (error as Error).message)
  }
}

// 关闭详情对话框
const handleDetailClose = () => {
  showDetailDialogVisible.value = false
  showDiffResult.value = false
  selectedRecord.value = null
  detailRecords.value = []
  currentOriginalPath.value = ''
  currentTargetPath.value = ''
}

// 组件挂载时获取数据
onMounted(() => {
  fetchHistory()
})
</script>

<style scoped>
.history-tab {
  max-width: 1600px;
  margin: 0 auto;
  padding: 20px;
}

/* 搜索区域 */
.search-section {
  margin-bottom: 20px;
}

.search-container {
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  padding: 24px;
}

.search-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 20px;
}

.search-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-label {
  color: #5a6c7d;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
}

.search-actions {
  display: flex;
  gap: 12px;
  margin-left: auto;
}

/* 历史记录部分 */
.history-section {
  margin-bottom: 20px;
}

.history-card {
  border: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  border-bottom: 1px solid #f0f2f5;
}

.card-header h3 {
  margin: 0;
  color: #2c3e50;
  font-size: 20px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-header .el-icon {
  color: #417FF2;
}

.history-list {
  padding: 24px;
  min-height: 400px;
}

.history-item {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
}

.history-item:hover {
  background: white;
  border-color: #417FF2;
  box-shadow: 0 4px 12px rgba(65, 127, 242, 0.15);
  transform: translateY(-2px);
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  color: #7f8c8d;
  font-size: 14px;
}

.item-time,
.item-ip {
  display: flex;
  align-items: center;
  gap: 6px;
}

.item-content {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 16px;
}

.file-group {
  flex: 1;
}

.file-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #5a6c7d;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 8px;
}

.file-list-with-download {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex: 1;
}

.file-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1;
}

.vs-divider {
  font-weight: 700;
  color: #417FF2;
  font-size: 18px;
  padding: 0 16px;
  position: relative;
  display: flex;
  align-items: center;
}

.item-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.item-actions .el-button {
  color: #417FF2;
  font-weight: 500;
}

.item-actions .el-button:hover {
  color: #2D5FD8;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 24px;
  border-top: 1px solid #f0f2f5;
}

/* 详情对话框 */
.detail-info {
  margin-bottom: 20px;
}

.diff-result {
  margin-top: 20px;
}

.diff-controls {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}

.loading-container {
  padding: 20px;
  text-align: center;
}

.loading-text {
  margin-top: 16px;
  color: #7f8c8d;
  font-size: 14px;
}

/* 响应式 */
@media (max-width: 768px) {
  .history-tab {
    padding: 16px;
  }

  .item-content {
    flex-direction: column;
    gap: 12px;
  }

  .vs-divider {
    padding: 8px 0;
  }

  .search-form {
    flex-direction: column;
    align-items: stretch;
  }

  .search-actions {
    margin-left: 0;
  }
}
</style>
